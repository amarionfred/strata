/* Native Linux session supervisor. SPDX-License-Identifier: MIT
 * Uses the running kernel: no emulation, virtual machine, or simulated UID.
 */
#define _GNU_SOURCE
#include <errno.h>
#include <fcntl.h>
#include <limits.h>
#include <mntent.h>
#include <poll.h>
#include <sched.h>
#include <signal.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/mount.h>
#include <sys/prctl.h>
#include <sys/stat.h>
#include <sys/types.h>
#include <sys/wait.h>
#include <unistd.h>

static volatile sig_atomic_t signal_target;
static volatile sig_atomic_t pending_signal;
static char root[PATH_MAX];

static unsigned long long start_time(void) {
    char buffer[4096];
    FILE *file = fopen("/proc/self/stat", "r");
    if (!file) return 0;
    if (!fgets(buffer, sizeof buffer, file)) { fclose(file); return 0; }
    fclose(file);
    char *field = strrchr(buffer, ')');
    if (!field) return 0;
    field += 2;
    for (int index = 3; index < 22; ++index) {
        field = strchr(field, ' ');
        if (!field) return 0;
        ++field;
    }
    return strtoull(field, NULL, 10);
}

static void die(const char *operation) {
    fprintf(stderr, "oma-session: %s: %s\n", operation, strerror(errno));
    exit(1);
}

static void require(int result, const char *operation) {
    if (result < 0) die(operation);
}

static void forward_signal(int number) {
    int saved = errno;
    if (number == SIGALRM) {
        if (signal_target) kill(signal_target, SIGKILL);
    } else {
        pending_signal = number;
        if (signal_target) kill(signal_target, number);
        alarm(5);
    }
    errno = saved;
}

static void install_handlers(void) {
    struct sigaction action = {.sa_handler = forward_signal};
    sigemptyset(&action.sa_mask);
    require(sigaction(SIGTERM, &action, NULL), "handle SIGTERM");
    require(sigaction(SIGINT, &action, NULL), "handle SIGINT");
    require(sigaction(SIGHUP, &action, NULL), "handle SIGHUP");
    require(sigaction(SIGALRM, &action, NULL), "handle shutdown timeout");
}

static int exit_code(int status) {
    if (WIFEXITED(status)) return WEXITSTATUS(status);
    return WIFSIGNALED(status) ? 128 + WTERMSIG(status) : 1;
}

static void path_in_root(char *destination, const char *relative) {
    if (snprintf(destination, PATH_MAX, "%s/%s", root, relative) >= PATH_MAX) {
        errno = ENAMETOOLONG;
        die("rootfs path");
    }
}

/* Mount targets must be real directories, never rootfs symlinks into the host. */
static void directory(const char *relative, mode_t mode) {
    char target[PATH_MAX];
    path_in_root(target, relative);
    if (mkdir(target, mode) < 0 && errno != EEXIST) die(target);
    struct stat st;
    require(lstat(target, &st), target);
    if (!S_ISDIR(st.st_mode)) {
        errno = EINVAL;
        die("mount target is not a directory");
    }
}

static void mount_at(const char *source, const char *relative, const char *type,
                     unsigned long flags, const char *options) {
    char target[PATH_MAX];
    path_in_root(target, relative);
    require(mount(source, target, type, flags, options), target);
}

static void bind_device(const char *name) {
    char source[PATH_MAX], target[PATH_MAX], relative[128];
    snprintf(source, sizeof source, "/dev/%s", name);
    snprintf(relative, sizeof relative, "dev/%s", name);
    path_in_root(target, relative);
    int fd = open(target, O_WRONLY | O_CREAT | O_EXCL | O_CLOEXEC, 0600);
    require(fd, target);
    close(fd);
    mount_at(source, relative, NULL, MS_BIND, NULL);
}

static void readonly_sysfs(void) {
    char target[PATH_MAX];
    path_in_root(target, "sys");
    /* A recursive bind preserves submounts that cannot be detached in a user
     * namespace. Remount each copy, without changing the original host mount. */
    mount_at("/sys", "sys", NULL, MS_BIND | MS_REC, NULL);
    FILE *mounts = setmntent("/proc/self/mounts", "r");
    if (!mounts) die("read mount table");
    size_t length = strlen(target);
    struct mntent *entry;
    while ((entry = getmntent(mounts))) {
        if (strncmp(entry->mnt_dir, target, length) ||
            (entry->mnt_dir[length] && entry->mnt_dir[length] != '/')) continue;
        unsigned long flags = MS_REMOUNT | MS_BIND | MS_RDONLY | MS_NOSUID | MS_NODEV | MS_NOEXEC;
        if (hasmntopt(entry, "noatime")) flags |= MS_NOATIME;
        if (hasmntopt(entry, "nodiratime")) flags |= MS_NODIRATIME;
        if (hasmntopt(entry, "relatime")) flags |= MS_RELATIME;
        require(mount(NULL, entry->mnt_dir, NULL, flags, NULL), "read-only sysfs view");
    }
    endmntent(mounts);
}

static void setup_mounts(void) {
    /* Only this namespace changes. Never propagate mounts into Android. */
    require(mount(NULL, "/", NULL, MS_REC | MS_PRIVATE, NULL), "private mounts");
    const char *targets[] = {"proc", "sys", "dev", "run", "tmp", NULL};
    for (int i = 0; targets[i]; ++i) directory(targets[i], 0755);
    mount_at("proc", "proc", "proc", MS_NOSUID | MS_NODEV | MS_NOEXEC, NULL);
    mount_at("tmpfs", "dev", "tmpfs", MS_NOSUID, "mode=0755,size=16m");
    const char *devices[] = {"null", "zero", "full", "random", "urandom", "tty", NULL};
    for (int i = 0; devices[i]; ++i) bind_device(devices[i]);
    directory("dev/pts", 0755);
    mount_at("devpts", "dev/pts", "devpts", MS_NOSUID | MS_NOEXEC,
             "newinstance,ptmxmode=0666,mode=0620");
    char target[PATH_MAX];
    path_in_root(target, "dev/ptmx");
    require(symlink("pts/ptmx", target), "dev/ptmx");
    path_in_root(target, "dev/fd");
    require(symlink("/proc/self/fd", target), "dev/fd");
    const char *streams[] = {"stdin", "stdout", "stderr"};
    for (int i = 0; i < 3; ++i) {
        char relative[64], link[64];
        snprintf(relative, sizeof relative, "dev/%s", streams[i]);
        snprintf(link, sizeof link, "/proc/self/fd/%d", i);
        path_in_root(target, relative);
        require(symlink(link, target), "standard stream link");
    }
    directory("dev/shm", 01777);
    mount_at("tmpfs", "dev/shm", "tmpfs", MS_NOSUID | MS_NODEV, "mode=1777,size=128m");
    mount_at("tmpfs", "run", "tmpfs", MS_NOSUID | MS_NODEV, "mode=0755,size=32m");
    mount_at("tmpfs", "tmp", "tmpfs", MS_NOSUID | MS_NODEV, "mode=1777,size=128m");
    /* Read the existing kernel's sysfs. A fresh sysfs mount can be forbidden
     * when a user namespace shares the host network namespace (as in tests). */
    readonly_sysfs();
}

/* PID 1 reaps children and forwards shutdown. Exiting destroys its PID namespace,
 * so double-forked daemons cannot survive a finished/stopped session. */
static int session_init(char **command) {
    signal_target = pending_signal = 0;
    install_handlers();
    require(setsid(), "session leader");
    setup_mounts();
    require(sethostname("oma-system", 10), "session hostname");
    require(chdir(root), "enter rootfs");
    require(chroot("."), "chroot");
    require(chdir("/"), "enter Linux root");
    pid_t child = fork();
    require(child, "start command");
    if (child == 0) {
        require(setpgid(0, 0), "command process group");
        signal(SIGTERM, SIG_DFL);
        signal(SIGINT, SIG_DFL);
        signal(SIGHUP, SIG_DFL);
        signal(SIGALRM, SIG_DFL);
        clearenv();
        setenv("PATH", "/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin", 1);
        setenv("HOME", "/root", 1);
        setenv("USER", "root", 1);
        setenv("LOGNAME", "root", 1);
        setenv("TERM", "xterm-256color", 1);
        setenv("LANG", "C.UTF-8", 1);
        if (chdir("/root") < 0) require(chdir("/"), "working directory");
        execvp(command[0], command);
        die("execute Linux command");
    }
    /* Either side may win this race; exec/exit can make the parent's call fail. */
    (void)setpgid(child, child);
    signal_target = -child;
    if (pending_signal) kill(-child, pending_signal);
    for (;;) {
        int status;
        pid_t reaped = waitpid(-1, &status, 0);
        if (reaped < 0) {
            if (errno == EINTR) continue;
            die("reap Linux process");
        }
        if (reaped == child) return exit_code(status);
    }
}

int main(int argc, char **argv) {
    const char *pidfile = NULL;
    if (argc > 2 && !strcmp(argv[1], "--pidfile")) {
        pidfile = argv[2];
        argc -= 2;
        argv += 2;
    }
    if (argc < 4 || strcmp(argv[2], "--") != 0) {
        fprintf(stderr, "usage: oma-session [--pidfile FILE] ROOTFS -- COMMAND [ARG ...]\n");
        return 2;
    }
    if (geteuid() != 0) {
        fprintf(stderr, "oma-session: real UID 0 is required; no root emulation is provided\n");
        return 1;
    }
    if (!realpath(argv[1], root)) die("resolve rootfs");
    if (!strcmp(root, "/")) {
        fprintf(stderr, "oma-session: refusing to use the host root as a Linux rootfs\n");
        return 1;
    }
    struct stat st;
    require(stat(root, &st), "inspect rootfs");
    if (!S_ISDIR(st.st_mode) || st.st_uid != 0 || (st.st_mode & 0022)) {
        fprintf(stderr, "oma-session: rootfs must be a root-owned directory, not writable by group/others\n");
        return 1;
    }
    install_handlers();
    require(unshare(CLONE_NEWNS | CLONE_NEWPID | CLONE_NEWUTS), "create native namespaces");
    int lifetime[2];
    require(pipe2(lifetime, O_CLOEXEC), "parent lifetime pipe");
    if (pidfile) {
        unsigned long long identity = start_time();
        if (!identity) { errno = EIO; die("read process identity"); }
        char boot_id[64];
        FILE *boot = fopen("/proc/sys/kernel/random/boot_id", "r");
        if (!boot || !fgets(boot_id, sizeof boot_id, boot)) die("read boot identity");
        fclose(boot);
        boot_id[strcspn(boot_id, "\n")] = 0;
        int fd = open(pidfile, O_WRONLY | O_CREAT | O_EXCL | O_NOFOLLOW | O_CLOEXEC, 0600);
        require(fd, "create PID file (a session may already be running)");
        if (dprintf(fd, "%ld %llu %s\n", (long)getpid(), identity, boot_id) < 0) die("write PID file");
        close(fd);
    }
    pid_t child = fork();
    require(child, "start namespace init");
    if (child == 0) {
        close(lifetime[1]);
        require(prctl(PR_SET_PDEATHSIG, SIGKILL), "bind session to supervisor lifetime");
        /* Covers a parent death before PR_SET_PDEATHSIG was installed. */
        struct pollfd lifetime_poll = {.fd = lifetime[0], .events = POLLIN};
        require(poll(&lifetime_poll, 1, 0), "check supervisor lifetime");
        if (lifetime_poll.revents & POLLHUP) _exit(125);
        close(lifetime[0]);
        _exit(session_init(argv + 3));
    }
    close(lifetime[0]);
    signal_target = child;
    if (pending_signal) kill(child, pending_signal);
    for (;;) {
        int status;
        if (waitpid(child, &status, 0) >= 0) {
            if (pidfile) unlink(pidfile);
            return exit_code(status);
        }
        if (errno != EINTR) die("wait for session");
    }
}
