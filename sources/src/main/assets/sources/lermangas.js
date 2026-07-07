export default {
  id: "lermangas",
  name: "Ler Mangas",
  lang: "pt-BR",
  baseUrl: "https://lermangas.me",

  search: function(query) {
    var url = this.baseUrl + "/?s=" + encodeURIComponent(query) + "&post_type=wp-manga";
    var html = SourceEnv.fetch(url);
    var items = SourceEnv.queryAll(html, ".c-tabs-item__content, .page-listing-item .row, .c-tabs-item, .item-summary");
    if (!items || items.length === 0) {
      return [];
    }
    var self = this;
    var results = [];
    for (var i = 0; i < items.length; i++) {
      var item = items[i];
      var linkEls = SourceEnv.queryAll(item.html, "a");
      var title = "";
      var href = "";
      for (var j = 0; j < linkEls.length; j++) {
        var a = linkEls[j];
        if (a.text && a.text.trim()) {
          if (!title) {
            title = a.text.trim();
            href = a.attrs && a.attrs.href ? a.attrs.href : "";
          }
        }
      }
      if (!title) {
        title = SourceEnv.queryAll(item.html, "h3 a, .post-title a, h4 a")[0];
        title = title ? (title.text || title.attrs && title.attrs.title || "") : "";
        href = title && title.attrs && title.attrs.href ? title.attrs.href : (item.attrs && item.attrs.href || "");
      }
      var imgEl = SourceEnv.queryAll(item.html, "img")[0];
      var coverUrl = imgEl && imgEl.attrs ? (imgEl.attrs["data-src"] || imgEl.attrs.src || "") : "";
      if (title && title !== "") {
        results.push({ title: title, coverUrl: coverUrl, url: href || title });
      }
    }
    if (results.length === 0) {
      var altItems = SourceEnv.queryAll(html, ".tab-thumb a, .item-thumb a, a[href*='manga'] img");
      if (altItems && altItems.length > 0) {
        for (var k = 0; k < altItems.length; k++) {
          var img = altItems[k];
          var parent = img.html || "";
          var imgSrc = img.attrs && (img.attrs["data-src"] || img.attrs.src) || "";
          var parentHref = "";
          var links = SourceEnv.queryAll(parent, "a");
          if (links && links.length > 0 && links[0].attrs) {
            parentHref = links[0].attrs.href || "";
          }
          if (img.attrs && img.attrs.alt) {
            results.push({ title: img.attrs.alt, coverUrl: imgSrc, url: parentHref || img.attrs.alt });
          }
        }
      }
    }
    return results;
  },

  getMangaDetail: function(url) {
    var detailUrl = url;
    if (!detailUrl.startsWith("http")) {
      if (detailUrl.startsWith("/")) {
        detailUrl = this.baseUrl + detailUrl;
      } else {
        detailUrl = this.baseUrl + "/manga/" + encodeURIComponent(detailUrl);
      }
    }
    var html = SourceEnv.fetch(detailUrl);
    var title = "";
    var titleEl = SourceEnv.queryAll(html, ".post-title h1, .entry-title, h1");
    if (titleEl && titleEl.length > 0) title = titleEl[0].text || "";
    var cover = "";
    var coverEl = SourceEnv.queryAll(html, ".summary_image img, .thumb img, .tab-thumb img");
    if (coverEl && coverEl.length > 0) cover = coverEl[0].attrs && (coverEl[0].attrs["data-src"] || coverEl[0].attrs.src) || "";
    var desc = "";
    var descEl = SourceEnv.queryAll(html, ".description-summary p, .summary__content p, .entry-content p");
    if (descEl && descEl.length > 0) {
      for (var d = 0; d < descEl.length; d++) {
        if (descEl[d].text && descEl[d].text.trim()) {
          desc = descEl[d].text.trim();
          break;
        }
      }
    }
    var status = "";
    var statusEl = SourceEnv.queryAll(html, ".post-status .summary-content, .manga-status .summary-content");
    if (statusEl && statusEl.length > 0) {
      status = statusEl[0].text || "";
    }
    var chRows = SourceEnv.queryAll(html, ".wp-manga-chapter a, .chapter-list a, .listing-chapters_wrap a");
    var seen = {};
    var chapters = [];
    for (var c = chRows.length - 1; c >= 0; c--) {
      var ch = chRows[c];
      var chTitle = ch.text ? ch.text.trim() : "";
      var chUrl = ch.attrs && ch.attrs.href ? ch.attrs.href : "";
      if (chTitle && !seen[chTitle]) {
        seen[chTitle] = true;
        var chNum = 0;
        var numMatch = chTitle.match(/(\d+(?:\.\d+)?)/);
        if (numMatch) chNum = parseFloat(numMatch[1]);
        chapters.push({
          id: chUrl || chTitle,
          title: chTitle,
          url: chUrl || chTitle,
          number: chNum
        });
      }
    }
    chapters.sort(function(a, b) { return b.number - a.number; });
    return {
      title: title || url,
      coverUrl: cover,
      description: desc,
      status: status,
      chapters: chapters
    };
  },

  getChapterPages: function(url) {
    var self = this;
    var pageUrl = url;
    if (!pageUrl.startsWith("http")) {
      pageUrl = this.baseUrl + (pageUrl.startsWith("/") ? pageUrl : "/" + pageUrl);
    }
    if (pageUrl.indexOf("?") < 0) {
      pageUrl = pageUrl + "?style=paged";
    }
    var html = SourceEnv.fetch(pageUrl);
    var scriptData = SourceEnv.queryAll(html, "#chapter_preloaded_images");
    if (scriptData && scriptData.length > 0) {
      var content = scriptData[0].html || scriptData[0].text || "";
      var start = content.indexOf("chapter_preloaded_images = [");
      if (start >= 0) {
        start = content.indexOf("[", start) + 1;
        var end = content.indexOf("]", start);
        if (end > start) {
          var jsonStr = content.substring(start, end);
          try {
            var urls = JSON.parse("[" + jsonStr + "]");
            return urls.map(function(u, i) {
              return { index: i, imageUrl: u, headers: { "Referer": self.baseUrl + "/" } };
            });
          } catch (e) {
          }
        }
      }
    }
    var imgs = SourceEnv.queryAll(html, ".reading-content img, .text-center img, .page-break img, .wp-manga-chapter-img");
    if (imgs && imgs.length > 0) {
      return imgs.map(function(img, i) {
        var src = img.attrs && (img.attrs["data-src"] || img.attrs.src || img.attrs["data-lazy-src"] || "");
        return { index: i, imageUrl: src, headers: { "Referer": self.baseUrl + "/" } };
      });
    }
    return [];
  }
};