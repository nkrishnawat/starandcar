/* ── EN → हिंदी (Hindi) word list + tiny swap engine. Fully local, no external service. ──
   Longer phrases are replaced first (runtime sort), so "Publish listing" never
   half-translates into "प्रकाशित करें listing". State = one key: localStorage 'lang' = 'hi'|'en'.
   index.html carries a guarded MIRROR copy of this file (srcdoc previews cannot load external
   files) - keep the two in sync. localStorage is wrapped in try/catch so this also runs inside srcdoc iframes. */

const HINDI = {
  /* long sentences first (order here does not matter - the engine sorts by length) */
  "STAR&Car is a free platform to post and discover short-term accommodation rentals and cars. Whether you're a property owner, professional, student or a traveller, we make it easy.": "STAR&Car अल्पकालिक आवास किराये और कार पोस्ट करने और खोजने का एक निःशुल्क मंच है। चाहे आप संपत्ति मालिक हों, पेशेवर, छात्र या यात्री — हम इसे आसान बनाते हैं।",
  "Save or send this link. Anyone with it can edit or delete this listing, so treat it like a private key.": "यह लिंक सहेजें या भेजें। इसे पाने वाला कोई भी इस लिस्टिंग को संपादित या हटा सकता है, इसलिए इसे निजी कुंजी की तरह रखें।",
  "No way to reach the owner of this listing yet - no mail device and no contact address.": "इस लिस्टिंग के मालिक तक पहुँचने का अभी कोई रास्ता नहीं - न कोई मेल डिवाइस, न संपर्क पता।",
  "Lease period, M/F per room, Shared kitchen, # of WashRoom and parking OR Vehicle's condition, sale/rent price…": "पट्टा अवधि, कमरे के अनुसार M/F, साझा रसोई, वॉशरूम और पार्किंग की संख्या या वाहन की स्थिति, बिक्री/किराया मूल्य…",
  "Describe the property, amenities, location OR Vehicle's Year/Make/Model…": "संपत्ति, सुविधाएँ, स्थान या वाहन का वर्ष/कंपनी/मॉडल बताएं…",
  "Update your listing details or remove it from STAR&Car.": "अपनी लिस्टिंग का विवरण अपडेट करें या इसे STAR&Car से हटाएँ।",
  "Add availability, pricing or other useful notes": "उपलब्धता, मूल्य या अन्य उपयोगी नोट्स जोड़ें",
  "Search by PIN / ZIP, city OR Vehicle Model…": "पिन / ज़िप, शहर या वाहन मॉडल से खोजें…",
  "Something went wrong. Please try again.": "कुछ गलत हो गया। कृपया पुनः प्रयास करें।",
  "A subject and a message are both required.": "विषय और संदेश दोनों आवश्यक हैं।",
  "Notes for guests and buyers": "मेहमानों और खरीदारों के लिए नोट्स",
  "Your Device ID (MAC / Machine ID)": "आपका डिवाइस ID (MAC / मशीन ID)",
  "— Short Term Accommodation Rental & Car": "— अल्पकालिक आवास किराया और कार",
  "Share your feedback or suggestions…": "अपनी प्रतिक्रिया या सुझाव साझा करें…",
  "Listing published successfully!": "लिस्टिंग सफलतापूर्वक प्रकाशित हुई!",
  "Your private listing link": "आपका निजी लिस्टिंग लिंक",
  "Select and copy the link.": "लिंक चुनें और कॉपी करें।",
  "Describe the property or vehicle": "संपत्ति या वाहन का विवरण दें",
  "Display name (optional)": "प्रदर्शन नाम (वैकल्पिक)",
  "Available count #": "उपलब्ध संख्या #",
  "YOU HAVE NEW MAIL IN INBOX": "आपके इनबॉक्स में नया संदेश है",
  "No received messages yet.": "अभी तक कोई प्राप्त संदेश नहीं।",
  "No results found for": "कोई परिणाम नहीं मिला:",
  "WAS YOUR CAR BYD SOLD?": "क्या आपकी BYD कार बिक गई?",
  "No sent messages yet.": "अभी तक कोई भेजा गया संदेश नहीं।",
  "PAYMENT DUE — 45000 RS": "बकाया भुगतान — 45000 रु.",
  "Keep this link safe.": "यह लिंक सुरक्षित रखें।",
  "Save & go online": "सहेजें और ऑनलाइन जाएँ",
  "WAS ROOM RENTED?": "क्या कमरा किराए पर दिया?",
  "Publish listing": "लिस्टिंग प्रकाशित करें",
  "Notes for guests": "मेहमानों के लिए नोट्स",
  "Send feedback": "प्रतिक्रिया भेजें",
  "Delete listing": "लिस्टिंग हटाएँ",
  "Upload images": "छवियाँ अपलोड करें",
  "Manage listing": "लिस्टिंग प्रबंधन",
  "About STAR&Car": "STAR&Car के बारे में",
  "Full street address": "पूरा सड़क पता",
  "Changes saved.": "परिवर्तन सहेजे गए।",
  "PAST SEARCHES — ": "पिछली खोजें — ",
  "INBOX TOTAL ": "इनबॉक्स कुल ",
  ":: RECEIVED ": ":: प्राप्त ",
  ":: SENT ": ":: भेजे गए ",
  "Link copied.": "लिंक कॉपी हो गया।",
  "Owner Notes": "मालिक के नोट्स",
  "Save changes": "परिवर्तन सहेजें",
  "New message": "नया संदेश",
  "Add listing": "लिस्टिंग जोड़ें",
  "Description": "विवरण",
  "Open editor": "एडिटर खोलें",
  "Copy link": "लिंक कॉपी करें",
  "REMIND": "याद दिलाएँ",
  "Received": "प्राप्त",
  "Publish": "प्रकाशित करें",
  "Address": "पता",
  "Subject": "विषय",
  "Message": "संदेश",
  "Images": "छवियाँ",
  "Inbox": "इनबॉक्स",
  "Search": "खोजें",
  "Email": "ईमेल",
  "About": "परिचय",
  "Phone": "फ़ोन",
  "Count": "संख्या",
  "Cancel": "रद्द करें",
  "Send": "भेजें",
  "Sent": "भेजे गए",
  "YES": "हाँ",
  "PAID": "भुगतान हुआ",
  "NO": "नहीं",
  "To": "को"
};

(function () {
  const PAIRS = Object.entries(HINDI).sort(function (a, b) { return b[0].length - a[0].length; });
  let cached = null;                       // [[node, original text], …] + [[field, original placeholder], …]

  const ls = {
    get: function (k) { try { return localStorage.getItem(k); } catch (e) { return null; } },
    set: function (k, v) { try { localStorage.setItem(k, v); } catch (e) { /* private/srcdoc */ } }
  };

  /* translate one string: longest English phrase wins */
  function tr(text) {
    PAIRS.forEach(function (p) { text = text.split(p[0]).join(p[1]); });
    return text;
  }

  function cache() {
    cached = { nodes: [], fields: [] };
    const walk = document.createTreeWalker(document.body, NodeFilter.SHOW_TEXT);
    let n;
    while ((n = walk.nextNode())) {
      if (/^(SCRIPT|STYLE)$/.test(n.parentNode.tagName)) continue;
      cached.nodes.push([n, n.nodeValue]);
    }
    document.querySelectorAll('[placeholder]').forEach(function (el) { cached.fields.push([el, el.placeholder]); });
  }

  /* for strings built at runtime (footer ticker, search messages) */
  window.toHindi = function (text) { return ls.get('lang') === 'hi' ? tr(text) : text; };

  /* the EN|LO switch: swap every text node + placeholder, and swap back */
  window.applyHindi = function (on) {
    ls.set('lang', on ? 'hi' : 'en');
    if (!cached) cache();
    cached.nodes.forEach(function (o) { o[0].nodeValue = on ? tr(o[1]) : o[1]; });
    cached.fields.forEach(function (o) { if (o[1]) o[0].placeholder = on ? tr(o[1]) : o[1]; });
  };

  document.addEventListener('DOMContentLoaded', function () {
    if (ls.get('lang') === 'hi') {
      applyHindi(true);
      const btn = document.getElementById('langSw');
      if (btn) btn.textContent = 'LO';
    }
  });
})();
