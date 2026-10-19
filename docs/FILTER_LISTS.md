# Filter lists and licenses

HISN blocks ads/trackers by matching the domain of every sub-resource request against a local rule set.

| Source | Use | License |
| --- | --- | --- |
| `app/src/main/assets/filters/default_hosts.txt` | Built-in starter list, works offline from first launch | Original compilation by Mohammed Hussein (© 2026) |
| [StevenBlack/hosts](https://github.com/StevenBlack/hosts) "unified hosts" (`https://raw.githubusercontent.com/StevenBlack/hosts/master/hosts`) | Downloaded on demand / weekly, cached in the app's private storage, never bundled | MIT, © Steven Black |

Behaviour worth knowing:

* Updates only run when the user taps **Update filter lists now**, or once a week in the background (after startup, never while a page loads). The setting can be turned off.
* A download is validated (size cap, at least 5,000 rules) before it replaces the working list; a failed update changes nothing.
* Main-frame navigations are never blocked, nor are requests to the page's own site, nor sites on the user's allowlist.
* No HTTPS interception, no installed certificates. This is domain blocking only: no cosmetic (element-hiding) filtering, and ads served from the same domain as the content cannot be blocked.
