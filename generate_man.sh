#!/bin/bash

# Check if a command is provided
if [ $# -eq 0 ]; then
    echo "Usage: $0 <command>"
    exit 1
fi

COMMAND=$1

# Verify that the command exists
if ! command -v "$COMMAND" &>/dev/null; then
    echo "Error: Command '$COMMAND' not found."
    exit 1
fi

# Get the current date
DATE=$(date +"%Y-%m-%d")

# Attempt to get the command's version
VERSION=$($COMMAND --version 2>/dev/null | head -n 1)

# Capture the help output
HELP_OUTPUT=$($COMMAND --help 2>&1)

# Create the man page content
MAN_CONTENT=".TH \"$COMMAND\" \"1\" \"$DATE\" \"${VERSION:-\"1.0\"}\" \"$COMMAND Manual\"
.SH NAME
$COMMAND \- autogenerated manual page
.SH SYNOPSIS
.B $COMMAND
[\fIoptions\fR]
.SH DESCRIPTION
$HELP_OUTPUT
"

# Define the man page directory and file
MAN_DIR="/usr/local/share/man/man1"
MAN_PAGE="$MAN_DIR/$COMMAND.1"

# Write the man page (requires sudo for permission)
echo "Creating man page at $MAN_PAGE..."
sudo mkdir -p "$MAN_DIR"
echo "$MAN_CONTENT" | sudo tee "$MAN_PAGE" >/dev/null

# Set appropriate permissions
sudo chmod 644 "$MAN_PAGE"

# Update the man database
echo "Updating man database..."
sudo mandb >/dev/null

echo "Man page for '$COMMAND' has been generated and installed."
