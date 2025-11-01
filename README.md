# Scale Converter

Scale Converter is a lightweight, fully client-side web tool for converting real-world dimensions to their equivalents on scaled drawings. The interface is primarily in Persian (Farsi) and supports both metric and imperial units with pre-configured architectural scales as well as a custom scale option.

## Features
- Convert between real-world measurements and drawing units across common architectural and engineering scales.
- Switch between preset ratios or enter a custom 1:N scale.
- Toggle between metric and imperial units.
- Light and dark theme toggle tailored for comfortable viewing.
- News popover for in-app announcements and update notes.
- Persisted conversion history for quick reuse of previous inputs.

## Usage
Open [`index.html`](index.html) in a modern browser. No build process or backend services are required—the app runs entirely in the browser.

## Project structure
- `index.html` – Single-page application that contains the UI, styling, and JavaScript logic for scale conversion.
- `favicon.*`, `apple-touch-icon*.png` – Icons for browsers and mobile devices.
- `Android.txt`, `windows.txt`, `news.txt` – Platform-specific metadata and news content displayed inside the application.

## Development
Since all logic lives inside `index.html`, you can edit the HTML, CSS, or embedded JavaScript directly to adjust the UI or conversion formulas. Use any static file server (or simply open the file locally) to preview changes.

## License
This project does not currently include an explicit license. Please add one if you plan to distribute or modify the tool publicly.
