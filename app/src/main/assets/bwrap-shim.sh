#!/bin/sh
# PRoot cannot provide Bubblewrap's namespaces. Never report isolation while
# running the requested command with unrestricted access to the guest's files.
printf '%s\n' 'Bubblewrap isolation is unavailable in this Android/PRoot runtime. The command was not run.' >&2
exit 78
