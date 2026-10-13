CC ?= cc
CFLAGS ?= -O2 -Wall -Wextra -Werror -Wformat=2 -fstack-protector-strong
LDFLAGS ?= -static
OUT ?= ../../.local/native/oma-session

.PHONY: all
all: $(OUT)

$(OUT): session.c
	mkdir -p $(dir $(OUT))
	$(CC) $(CFLAGS) session.c $(LDFLAGS) -o $(OUT)
