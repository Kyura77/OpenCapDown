export default {
  id: "mangadex",
  name: "MangaDex",
  lang: "multi",
  baseUrl: "https://api.mangadex.org",

  search: function(query) {
    var res = SourceEnv.fetch(this.baseUrl + "/manga?title=" + encodeURIComponent(query) + "&limit=10&includes[]=cover_art");
    var json = JSON.parse(res);
    var items = json.data || [];
    return items.map(function(m) {
      var title = (m.attributes.title && m.attributes.title.en) || Object.values(m.attributes.title)[0] || m.id;
      var coverFile = "";
      var rels = m.relationships || [];
      for (var i = 0; i < rels.length; i++) {
        if (rels[i].type === "cover_art" && rels[i].attributes) {
          coverFile = rels[i].attributes.fileName;
          break;
        }
      }
      var coverUrl = coverFile ? ("https://uploads.mangadex.org/covers/" + m.id + "/" + coverFile + ".256.jpg") : "";
      return {
        title: title,
        coverUrl: coverUrl,
        url: m.id
      };
    });
  },

  getMangaDetail: function(url) {
    var id = url;
    var res = SourceEnv.fetch(this.baseUrl + "/manga/" + id + "?includes[]=cover_art");
    var json = JSON.parse(res);
    var data = json.data || {};
    var title = (data.attributes && data.attributes.title && data.attributes.title.en) || Object.values(data.attributes.title)[0] || id;
    var coverFile = "";
    var rels = data.relationships || [];
    for (var i = 0; i < rels.length; i++) {
      if (rels[i].type === "cover_art" && rels[i].attributes) {
        coverFile = rels[i].attributes.fileName;
        break;
      }
    }
    var coverUrl = coverFile ? ("https://uploads.mangadex.org/covers/" + id + "/" + coverFile + ".256.jpg") : "";
    var desc = (data.attributes && data.attributes.description && data.attributes.description.en) || "";
    var status = (data.attributes && data.attributes.status) || "";
    var chRes = SourceEnv.fetch(this.baseUrl + "/manga/" + id + "/feed?translatedLanguage[]=pt-br&translatedLanguage[]=en&limit=200&order[chapter]=desc");
    var chJson = JSON.parse(chRes);
    var chapters = (chJson.data || []).map(function(c) {
      var num = parseFloat(c.attributes.chapter) || 0;
      var cid = c.id;
      return {
        id: cid,
        title: c.attributes.title || ("Cap. " + num),
        url: c.id,
        number: num
      };
    });
    return {
      title: title,
      coverUrl: coverUrl,
      description: desc,
      status: status,
      chapters: chapters
    };
  },

  getChapterPages: function(url) {
    var id = url;
    var res = SourceEnv.fetch("https://api.mangadex.org/at-home/server/" + id);
    var json = JSON.parse(res);
    var baseUrl2 = json.baseUrl;
    var hash = json.chapter.hash;
    var files = json.chapter.data || [];
    var base = baseUrl2 + "/data/" + hash + "/";
    return files.map(function(f, i) {
      return { index: i, imageUrl: base + f, headers: {} };
    });
  }
}