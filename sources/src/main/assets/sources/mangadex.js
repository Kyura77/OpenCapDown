export default {
  id: "mangadex",
  name: "MangaDex",
  lang: "multi",
  baseUrl: "https://api.mangadex.org",

  search(query) {
    var res = SourceEnv.fetch(this.baseUrl + "/manga?title=" + encodeURIComponent(query) + "&limit=10");
    var json = JSON.parse(res);
    return json.data.map(function(m) {
      return {
        title: m.attributes.title.en || Object.values(m.attributes.title)[0],
        coverUrl: m.attributes.links ? ("https://mangadex.org/covers/" + m.id + "/" + m.attributes.links.cover + ".256.jpg") : "",
        url: "https://mangadex.org/title/" + m.id
      };
    });
  },

  getMangaDetail(url) {
    var id = url.split("/").pop();
    var res = SourceEnv.fetch(this.baseUrl + "/manga/" + id + "?includes[]=cover_art");
    var json = JSON.parse(res);
    var title = json.data.attributes.title.en || Object.values(json.data.attributes.title)[0] || id;
    var cover = "";
    var chRes = SourceEnv.fetch(this.baseUrl + "/manga/" + id + "/feed?translatedLanguage[]=pt-br&translatedLanguage[]=en&limit=500&order[chapter]=desc");
    var chJson = JSON.parse(chRes);
    var chapters = (chJson.data || []).map(function(c) {
      var num = parseFloat(c.attributes.chapter) || 0;
      return {
        id: id + "-ch-" + num,
        title: c.attributes.title || "Cap. " + num,
        url: "https://mangadex.org/chapter/" + c.id,
        number: num
      };
    });
    return {
      title: title,
      coverUrl: cover,
      description: json.data.attributes.description.en || "",
      status: json.data.attributes.status || "",
      chapters: chapters
    };
  },

  getChapterPages(url) {
    var id = url.split("/").pop();
    var res = SourceEnv.fetch("https://api.mangadex.org/at-home/server/" + id);
    var json = JSON.parse(res);
    var base = json.baseUrl + "/data/" + json.chapter.hash + "/";
    return (json.chapter.data || []).map(function(f, i) {
      return { index: i, imageUrl: base + f, headers: {} };
    });
  }
}
