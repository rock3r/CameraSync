#!/usr/bin/env python3
"""
Scrapes Ricoh Imaging website to extract latest firmware versions for Ricoh cameras.

This script fetches firmware information from:
https://www.ricoh-imaging.co.jp/english/support/download_digital.html

It extracts firmware versions from tables under:
- Firmware Update (Digital SL Cameras)
- Firmware Update (Digital Compact Cameras)

Outputs a JSON file with camera model names and their latest firmware versions.
"""

import json
import re
import sys
from datetime import datetime, timezone
from typing import Dict

import requests
from bs4 import BeautifulSoup


def normalize_model_name(model_name: str) -> str:
    """
    Normalize camera model names from the website to match app conventions.
    
    The website uses names like "GR III", "GR IIIx", etc.
    The app may use "RICOH GR III", "RICOH GR IIIx", etc.
    For now, we keep the website names as-is and let the app handle mapping.
    """
    # Remove extra whitespace
    model_name = re.sub(r'\s+', ' ', model_name.strip())
    
    # Handle special cases if needed
    # For example, website might have "GR III" but app expects "RICOH GR III"
    # We'll keep it as-is for now and let the app handle the mapping
    
    return model_name


def extract_firmware_from_table(table) -> Dict[str, str]:
    """
    Extract camera model names and firmware versions from a firmware table.
    
    Args:
        table: BeautifulSoup table element
        
    Returns:
        Dictionary mapping camera model names to firmware versions
    """
    cameras = {}
    
    # Find all rows in the table (skip header row)
    rows = table.find_all('tr')[1:]  # Skip header row
    
    for row in rows:
        cells = row.find_all('td')
        if len(cells) >= 3:
            # Column structure: Digital cameras | Content | Version
            model_name = cells[0].get_text(strip=True)
            version = cells[2].get_text(strip=True)
            
            if model_name and version:
                normalized_name = normalize_model_name(model_name)
                cameras[normalized_name] = version
    
    return cameras


def scrape_ricoh_firmware() -> Dict[str, str]:
    """
    Scrape Ricoh Imaging website for firmware versions.
    
    Returns:
        Dictionary mapping camera model names to firmware versions
    """
    url = "https://www.ricoh-imaging.co.jp/english/support/download_digital.html"
    
    print(f"Fetching {url}...")
    try:
        response = requests.get(url, timeout=30)
        response.raise_for_status()
    except requests.RequestException as e:
        print(f"Error fetching website: {e}", file=sys.stderr)
        sys.exit(1)
    
    soup = BeautifulSoup(response.content, 'html.parser')
    
    all_cameras = {}
    
    # Find all headings (h4, h5, h6) that contain "Firmware Update"
    headings = soup.find_all(['h4', 'h5', 'h6'])
    
    for heading in headings:
        heading_text = heading.get_text(strip=True)
        
        # Look for firmware update sections
        if 'Firmware Update' in heading_text and 'Digital' in heading_text:
            # Find the next table after this heading using find_next
            table = heading.find_next('table')
            if table:
                print(f"Processing table under: {heading_text}")
                cameras = extract_firmware_from_table(table)
                all_cameras.update(cameras)
    
    if not all_cameras:
        print("Warning: No firmware data found. Website structure may have changed.", file=sys.stderr)
    
    return all_cameras


def main():
    """Main entry point."""
    print("Scraping Ricoh firmware versions...")

    cameras = scrape_ricoh_firmware()

    if not cameras:
        print(
            "Error: No firmware data found. Website structure may have changed. Refusing to deploy empty data.",
            file=sys.stderr,
        )
        sys.exit(1)

    # Generate output JSON
    output = {
        "last_updated": datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"),
        "cameras": cameras,
    }

    # Write to file
    output_file = "ricoh_firmware.json"
    with open(output_file, "w", encoding="utf-8") as f:
        json.dump(output, f, indent=2, ensure_ascii=False)

    print(f"\nFound {len(cameras)} camera models with firmware versions")
    print(f"Output written to {output_file}")

    # Print summary of GR models (most relevant for this app)
    gr_models = {k: v for k, v in cameras.items() if "GR" in k.upper()}
    if gr_models:
        print("\nGR Series firmware versions:")
        for model, version in sorted(gr_models.items()):
            print(f"  {model}: {version}")


if __name__ == "__main__":
    main()
