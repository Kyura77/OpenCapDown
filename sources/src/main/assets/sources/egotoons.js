export default {
  id: "egotoons",
  name: "Ego Toons",
  lang: "pt-BR",
  baseUrl: "https://www.egotoons.com",

  search: function(query) {
    var url = this.baseUrl + "/api/manga/search?query=" + encodeURIComponent(query) + "&offset=0&limit=20&withHentai=true";
    var res = SourceEnv.fetch(url);
    var json = JSON.parse(res);
    var items = json.items || [];
    var self = this;
    return items.map(function(m) {
      return {
        title: m.title,
        coverUrl: m.cover,
        url: "obra/" + m.id
      };
    });
  },

  getMangaDetail: function(url) {
    var mangaId = "";
    if (url.indexOf("obra/") >= 0) {
      mangaId = url.split("obra/")[1].split("/")[0];
    } else {
      mangaId = url;
    }
    var res = SourceEnv.fetch(this.baseUrl + "/api/manga/" + mangaId);
    var json = JSON.parse(res);
    var data = json;
    var statusMap = {
      "IN_PROGRESS": "Em publicação",
      "HIATUS": "Hiato",
      "COMPLETED": "Completo",
      "CANCELLED": "Cancelado"
    };
    var status = statusMap[data.status] || data.status || "";
    var chaptersUrl = this.baseUrl + "/api/manga/" + mangaId + "/chapter?limit=200&offset=0";
    var chRes = SourceEnv.fetch(chaptersUrl);
    var chJson = JSON.parse(chRes);
    var chItems = chJson.items || [];
    var chapters = chItems.map(function(c) {
      var num = c.chapter || 0;
      var title = c.title && c.title !== "" ? "Cap. " + num + " - " + c.title : "Cap. " + num;
      return {
        id: c.id + "",
        title: title,
        url: "obra/" + mangaId + "/capitulo/" + num.toString().replace(/\.0$/, ""),
        number: parseFloat(num)
      };
    });
    chapters.sort(function(a, b) { return b.number - a.number; });
    return {
      title: data.title,
      coverUrl: data.cover,
      description: data.synopsis || "",
      status: status,
      chapters: chapters
    };
  },

  getChapterPages: function(url) {
    var self = this;
    var parts = url.split("/");
    var mangaId = "";
    var chapterNum = "";
    for (var i = 0; i < parts.length; i++) {
      if (parts[i] === "obra" && i + 1 < parts.length) {
        mangaId = parts[i + 1];
      }
      if (parts[i] === "capitulo" && i + 1 < parts.length) {
        chapterNum = parts[i + 1];
      }
    }
    if (!mangaId && parts.length > 0) {
      mangaId = parts[parts.length - 3] || parts[0];
    }
    if (!chapterNum && parts.length > 0) {
      chapterNum = parts[parts.length - 1];
    }
    var imgUrl = this.baseUrl + "/api/manga/" + mangaId + "/chapter/" + chapterNum + "/images";
    var res = SourceEnv.fetch(imgUrl, {
      headers: {
        "x-mymangas-csrf-secure": "true",
        "x-mymangas-secure-panel-domain": "true"
      }
    });
    var urls = JSON.parse(res);
    return urls.map(function(u, i) {
      return {
        index: i,
        imageUrl: u,
        headers: {
          "Referer": self.baseUrl + "/",
          "x-mymangas-csrf-secure": "true",
          "x-mymangas-secure-panel-domain": "true"
        }
      };
    });
  }
};