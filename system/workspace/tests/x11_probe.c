/* A real X11 client for checking display pixels, pointer events and key input. */
#include <X11/Xlib.h>
#include <X11/Xutil.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

int main(void) {
    Display *display = XOpenDisplay(NULL);
    if (!display) return 2;
    Window window = XCreateSimpleWindow(display, DefaultRootWindow(display), 10, 10,
                                       300, 160, 0, 0, 0x205050);
    XStoreName(display, window, "Strata native graphics check");
    XSelectInput(display, window, ExposureMask | KeyPressMask | ButtonPressMask);
    XMapWindow(display, window);
    XFlush(display);
    GC gc = XCreateGC(display, window, 0, NULL);
    XSetForeground(display, gc, 0xffffff);
    for (;;) {
        XEvent event;
        XNextEvent(display, &event);
        if (event.type == Expose) {
            const char *text = "Native X11 program in Strata";
            XDrawString(display, window, gc, 15, 35, text, strlen(text));
        } else if (event.type == ButtonPress) {
            XSetInputFocus(display, window, RevertToParent, CurrentTime);
            FILE *out = fopen("gui-pointer", "w");
            if (out) { fputs("clicked", out); fclose(out); }
        } else if (event.type == KeyPress) {
            char buffer[64];
            int count = XLookupString(&event.xkey, buffer, sizeof(buffer), NULL, NULL);
            FILE *out = fopen("gui-keys", "a");
            if (out) { fwrite(buffer, 1, count, out); fclose(out); }
        }
        XFlush(display);
    }
}
