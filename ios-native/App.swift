import UIKit
import WebKit
import Network

@main
final class AppDelegate: UIResponder, UIApplicationDelegate {
    var window: UIWindow?
    func application(_ application: UIApplication, didFinishLaunchingWithOptions options: [UIApplication.LaunchOptionsKey: Any]?) -> Bool {
        window = UIWindow(frame: UIScreen.main.bounds)
        window?.rootViewController = ConverterController()
        window?.makeKeyAndVisible()
        return true
    }
}

final class ConverterController: UIViewController, WKNavigationDelegate, WKUIDelegate {
    private let site = URL(string: "https://pixelarchitecturestudio.github.io/ScaleConverter/")!
    private let monitor = NWPathMonitor()
    private let banner = UILabel()
    private let host = UIView()
    private let loadingOverlay = UIView()
    private var receivedNetworkStatus = false
    private var startupDeadline: DispatchWorkItem?
    private var current: WKWebView?
    private var candidate: WKWebView?
    private var offline = true
    private var connected = false
    private var timer: Timer?
    private var timeout: DispatchWorkItem?
    private var pendingState: String?
    private var loadingLocal = false

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = .systemBackground
        banner.text = "در حال استفاده از نسخه آفلاین هستید؛ ممکن است این نسخه نسبت به نسخه آنلاین قدیمی‌تر باشد."
        banner.font = .systemFont(ofSize: 12)
        banner.textAlignment = .center
        banner.numberOfLines = 0
        banner.textColor = .secondaryLabel
        banner.backgroundColor = .secondarySystemBackground
        banner.accessibilityIdentifier = "offlineNotice"
        let stack = UIStackView(arrangedSubviews: [host, banner])
        stack.axis = .vertical
        stack.spacing = 8
        stack.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(stack)
        NSLayoutConstraint.activate([
            stack.topAnchor.constraint(equalTo: view.safeAreaLayoutGuide.topAnchor),
            stack.bottomAnchor.constraint(equalTo: view.safeAreaLayoutGuide.bottomAnchor, constant: -4),
            stack.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            stack.trailingAnchor.constraint(equalTo: view.trailingAnchor)
        ])
        banner.setContentHuggingPriority(.required, for: .vertical)
        banner.setContentCompressionResistancePriority(.required, for: .vertical)
        banner.isHidden = true
        configureLoading()
        #if DEBUG
        if ProcessInfo.processInfo.arguments.contains("-PreviewLoading") { return }
        if ProcessInfo.processInfo.arguments.contains("-PreviewOffline") { loadOffline(); return }
        #endif
        let deadline = DispatchWorkItem { [weak self] in
            guard let self = self, !self.receivedNetworkStatus else { return }
            self.loadOffline()
        }
        startupDeadline = deadline
        DispatchQueue.main.asyncAfter(deadline: .now() + 4, execute: deadline)
        monitor.pathUpdateHandler = { [weak self] path in
            DispatchQueue.main.async {
                guard let self = self else { return }
                self.receivedNetworkStatus = true
                self.startupDeadline?.cancel()
                self.connected = path.status == .satisfied
                if self.connected { self.tryOnline() }
                else {
                    self.cancelCandidate()
                    if self.current == nil || !self.offline { self.loadOffline() }
                }
            }
        }
        monitor.start(queue: DispatchQueue(label: "ScaleConverter.network"))
        timer = Timer.scheduledTimer(withTimeInterval: 20, repeats: true) { [weak self] _ in
            guard let self = self, self.connected, self.offline else { return }
            self.tryOnline()
        }
        NotificationCenter.default.addObserver(self, selector: #selector(resume), name: UIApplication.didBecomeActiveNotification, object: nil)
    }
    private func configureLoading() {
        loadingOverlay.backgroundColor = .systemBackground
        loadingOverlay.translatesAutoresizingMaskIntoConstraints = false
        loadingOverlay.accessibilityIdentifier = "startupLoading"
        view.addSubview(loadingOverlay)
        NSLayoutConstraint.activate([
            loadingOverlay.topAnchor.constraint(equalTo: view.topAnchor),
            loadingOverlay.bottomAnchor.constraint(equalTo: view.bottomAnchor),
            loadingOverlay.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            loadingOverlay.trailingAnchor.constraint(equalTo: view.trailingAnchor)
        ])
        let title = UILabel()
        title.text = "Scale Converter"
        title.font = .systemFont(ofSize: 23, weight: .semibold)
        title.textColor = .label
        let spinner = UIActivityIndicatorView(style: .medium)
        spinner.startAnimating()
        let subtitle = UILabel()
        subtitle.text = "در حال بارگذاری…"
        subtitle.font = .systemFont(ofSize: 14)
        subtitle.textColor = .secondaryLabel
        let content = UIStackView(arrangedSubviews: [title, spinner, subtitle])
        content.axis = .vertical
        content.alignment = .center
        content.spacing = 20
        content.translatesAutoresizingMaskIntoConstraints = false
        loadingOverlay.addSubview(content)
        NSLayoutConstraint.activate([
            content.centerXAnchor.constraint(equalTo: loadingOverlay.centerXAnchor),
            content.centerYAnchor.constraint(equalTo: loadingOverlay.centerYAnchor)
        ])
    }
    private func finishLoading() {
        loadingOverlay.isHidden = true
    }
    private func onlineFailed() {
        cancelCandidate()
        if current == nil { loadOffline() }
    }
    @objc private func resume() { if connected && offline { tryOnline() } }
    private func makeWebView() -> WKWebView {
        let config = WKWebViewConfiguration()
        config.websiteDataStore = .default()
        let web = WKWebView(frame: .zero, configuration: config)
        web.navigationDelegate = self
        web.uiDelegate = self
        web.isOpaque = false
        web.backgroundColor = .systemBackground
        web.scrollView.contentInsetAdjustmentBehavior = .never
        web.translatesAutoresizingMaskIntoConstraints = false
        return web
    }
    private func mount(_ web: WKWebView) {
        host.addSubview(web)
        NSLayoutConstraint.activate([
            web.topAnchor.constraint(equalTo: host.topAnchor), web.bottomAnchor.constraint(equalTo: host.bottomAnchor),
            web.leadingAnchor.constraint(equalTo: host.leadingAnchor), web.trailingAnchor.constraint(equalTo: host.trailingAnchor)
        ])
    }
    // Carry form fields, preferences and history across the two origins.
    private func capture(_ completion: @escaping (String?) -> Void) {
        guard let web = current else { completion(nil); return }
        let js = """
        JSON.stringify({storage:Object.fromEntries(Object.keys(localStorage).map(k=>[k,localStorage.getItem(k)])),fields:Array.from(document.querySelectorAll('input[id],select[id],textarea[id]')).map(e=>({id:e.id,value:e.value,checked:e.checked})),y:scrollY})
        """
        web.evaluateJavaScript(js) { result, _ in completion(result as? String) }
    }
    private func seed(_ web: WKWebView, state: String?) {
        guard let state = state else { return }
        let js = "try { const s=\(state); Object.entries(s.storage).forEach(([k,v])=>localStorage.setItem(k,v)); } catch(e) {}"
        web.configuration.userContentController.addUserScript(WKUserScript(source: js, injectionTime: .atDocumentStart, forMainFrameOnly: true))
    }
    private func restore(_ web: WKWebView, state: String?) {
        guard let state = state else { return }
        let js = """
        (()=>{try{const s=\(state); Object.entries(s.storage).forEach(([k,v])=>localStorage.setItem(k,v)); for(const f of s.fields){const e=document.getElementById(f.id);if(e){e.value=f.value;if(typeof f.checked==='boolean')e.checked=f.checked;e.dispatchEvent(new Event('change',{bubbles:true}));e.dispatchEvent(new Event('input',{bubbles:true}));}}scrollTo(0,s.y);}catch(e){}})()
        """
        web.evaluateJavaScript(js, completionHandler: nil)
    }
    private func loadOffline() {
        guard !loadingLocal else { return }
        loadingLocal = true
        cancelCandidate()
        capture { [weak self] state in
            guard let self = self else { return }
            self.pendingState = state
            let web = self.makeWebView()
            self.seed(web, state: state)
            self.current?.stopLoading()
            self.current?.removeFromSuperview()
            self.current = web
            self.offline = true
            self.banner.isHidden = false
            self.mount(web)
            let url = Bundle.main.url(forResource: "index", withExtension: "html", subdirectory: "Offline")!
            web.loadFileURL(url, allowingReadAccessTo: url.deletingLastPathComponent())
        }
    }
    private func tryOnline() {
        guard connected, offline, candidate == nil, !loadingLocal else { return }
        let web = makeWebView()
        candidate = web
        capture { [weak self, weak web] state in
            guard let self = self, let web = web, self.candidate === web else { return }
            self.seed(web, state: state)
            var request = URLRequest(url: self.site, cachePolicy: .reloadIgnoringLocalCacheData, timeoutInterval: 8)
            request.setValue("no-cache", forHTTPHeaderField: "Cache-Control")
            web.load(request)
            let task = DispatchWorkItem { [weak self, weak web] in
                if let self = self, self.candidate === web { self.onlineFailed() }
            }
            self.timeout = task
            DispatchQueue.main.asyncAfter(deadline: .now() + 10, execute: task)
        }
    }
    private func cancelCandidate() {
        timeout?.cancel(); timeout = nil
        candidate?.stopLoading(); candidate = nil
    }
    func webView(_ webView: WKWebView, didFinish navigation: WKNavigation!) {
        if webView === candidate {
            capture { [weak self, weak webView] state in
                guard let self = self, let web = webView, self.candidate === web, self.connected else { return }
                self.timeout?.cancel()
                self.restore(web, state: state)
                self.current?.removeFromSuperview()
                self.current = web
                self.candidate = nil
                self.offline = false
                self.banner.isHidden = true
                self.mount(web)
                self.finishLoading()
            }
        } else if webView === current && offline {
            loadingLocal = false
            restore(webView, state: pendingState)
            pendingState = nil
            finishLoading()
        }
    }
    func webView(_ webView: WKWebView, didFailProvisionalNavigation navigation: WKNavigation!, withError error: Error) { failed(webView, error) }
    func webView(_ webView: WKWebView, didFail navigation: WKNavigation!, withError error: Error) { failed(webView, error) }
    private func failed(_ web: WKWebView, _ error: Error) {
        guard (error as NSError).code != NSURLErrorCancelled else { return }
        if web === candidate { onlineFailed() }
        else if web === current && !offline { loadOffline() }
    }
    func webViewWebContentProcessDidTerminate(_ webView: WKWebView) {
        if webView === candidate { onlineFailed() }
        else { loadingLocal = false; loadOffline() }
    }
    func webView(_ webView: WKWebView, decidePolicyFor response: WKNavigationResponse, decisionHandler: @escaping (WKNavigationResponsePolicy) -> Void) {
        if response.isForMainFrame, let http = response.response as? HTTPURLResponse, !(200...299).contains(http.statusCode) {
            decisionHandler(.cancel)
            if webView === candidate { onlineFailed() }
            else if !offline { loadOffline() }
        } else { decisionHandler(.allow) }
    }
    func webView(_ webView: WKWebView, decidePolicyFor action: WKNavigationAction, decisionHandler: @escaping (WKNavigationActionPolicy) -> Void) {
        guard let url = action.request.url else { decisionHandler(.cancel); return }
        if action.navigationType == .linkActivated || action.targetFrame == nil {
            let internalURL = url.isFileURL || (url.scheme == "https" && url.host == site.host && url.path.hasPrefix(site.path))
            if !internalURL {
                if ["https", "http", "mailto", "tel"].contains(url.scheme ?? "") { UIApplication.shared.open(url) }
                decisionHandler(.cancel); return
            }
        }
        decisionHandler(.allow)
    }
    func webView(_ webView: WKWebView, createWebViewWith configuration: WKWebViewConfiguration, for action: WKNavigationAction, windowFeatures: WKWindowFeatures) -> WKWebView? {
        if action.targetFrame == nil { webView.load(action.request) }
        return nil
    }
    deinit { startupDeadline?.cancel(); monitor.cancel(); timer?.invalidate(); NotificationCenter.default.removeObserver(self) }
}
