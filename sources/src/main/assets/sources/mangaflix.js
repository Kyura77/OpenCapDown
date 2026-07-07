export default {
  id: "mangaflix",
  name: "MangaFlix",
  lang: "pt-BR",
  baseUrl: "https://mangaflix.net",
  apiUrl: "https://api.mangaflix.net/v1",

  search: function(query) {
    var url = this.apiUrl + "/search/mangas?query=" + encodeURIComponent(query) + "&selected_language=pt-br";
    var res = SourceEnv.fetch(url);
    var json = JSON.parse(res);
    var data = json.data || json;
    var works = data.works || [];
    var self = this;
    return works.map(function(m) {
      return {
        title: m.name,
        coverUrl: m.poster && m.poster.default_url ? m.poster.default_url : "",
        url: "/br/manga/" + m._id
      };
    });
  },

  getMangaDetail: function(url) {
    var id = url.split("/").pop();
    var res = SourceEnv.fetch(this.apiUrl + "/mangas/" + id);
    var json = JSON.parse(res);
    var data = json.data || json;
    var chapters = (data.chapters || []).map(function(c) {
      var num = parseFloat(c.number) || 0;
      return {
        id: c._id,
        title: c.name && c.name !== "" ? c.name : "Cap. " + c.number,
        url: "/br/manga/" + c._id,
        number: num
      };
    });
    chapters.sort(function(a, b) { return b.number - a.number; });
    var genreStr = "";
    if (data.genres) {
      genreStr = data.genres.map(function(g) { return g.name || g; }).join(", ");
    }
    return {
      title: data.name || "",
      coverUrl: data.poster && data.poster.default_url ? data.poster.default_url : "",
      description: data.description || "",
      status: "",
      chapters: chapters
    };
  },

  getChapterPages: function(url) {
    var id = url.split("/").pop();
    var res = SourceEnv.fetch(this.apiUrl + "/chapters/" + id + "?selected_language=pt-br");
    var json = JSON.parse(res);
    var data = json.data || json;
    var images = data.images || [];
    return images.map(function(img, i) {
      return {
        index: i,
        imageUrl: img.default_url || img.imageUrl || img.url || "",
        headers: {}
      };
    });
  }
};