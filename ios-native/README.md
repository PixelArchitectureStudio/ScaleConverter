# Scale Converter for iOS and iPadOS

Fresh UIKit/WKWebView wrapper, built from source without ios_source.tar.gz.
Supports iOS/iPadOS 15+, portrait and landscape, and iLoader signing.

The bundled index.html works on first launch without internet. The app attempts the live GitHub Pages site when a network is available and displays it only after successful navigation. On disconnection or navigation failure it uses the bundled copy and shows a Persian offline notice below the page. An offline session retries every 20 seconds while connected. External links open in the system browser. No browser toolbar is added.

The prepare script bundles the checkout's HTML, icon and Vazirmatn font. This offline snapshot stays fixed until a new IPA is installed; live website updates require no IPA rebuild. Form fields and localStorage are copied during switching, but exact UI state across future website redesigns is not guaranteed.

Build: install XcodeGen and Python Pillow, run `python ios-native/prepare.py`, then `cd ios-native && xcodegen generate`. The GitHub Actions workflow builds and packages an unsigned arm64 IPA to be signed by iLoader. Free signing expiration is independent of online/offline operation.

Device acceptance: first launch in airplane mode; enter a calculation; reconnect and confirm the banner disappears; disconnect and confirm offline calculation; rotate on iPhone and iPad; open external links; force quit and reopen. Simulator/device acceptance is separate from successful compilation.
