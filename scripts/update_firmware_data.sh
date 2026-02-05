#!/bin/bash
# Convenience script to run the Ricoh firmware scraper locally.
# This generates ricoh_firmware.json in the scripts/ directory.
#
# To deploy to GitHub Pages, use the GitHub Actions workflow instead:
# - Via GitHub UI: Actions tab -> "Update Firmware Data" -> "Run workflow"
# - Via GitHub CLI: gh workflow run update_firmware_data.yml

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

VENV_DIR="$SCRIPT_DIR/.venv"

# Create virtual environment if it doesn't exist
if [ ! -d "$VENV_DIR" ]; then
    echo "Creating virtual environment..."
    python3 -m venv "$VENV_DIR"
fi

# Activate virtual environment
source "$VENV_DIR/bin/activate"

echo "Installing Python dependencies..."
pip install -q --upgrade pip
pip install -q -r requirements.txt

echo ""
echo "Running firmware scraper..."
python scrape_ricoh_firmware.py

echo ""
echo "✓ Success! Firmware data written to: $SCRIPT_DIR/ricoh_firmware.json"
