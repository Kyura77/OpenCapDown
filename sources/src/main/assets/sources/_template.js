export default {
  id: "template",
  name: "Template Source",
  lang: "pt-BR",
  baseUrl: "https://example.com",

  search: function(query) {
    SourceEnv.log("info", "search: " + query);
    return [];
  },

  getMangaDetail: function(url) {
    SourceEnv.log("info", "detail: " + url);
    return { title: "", coverUrl: "", description: "", status: "", chapters: [] };
  },

  getChapterPages: function(url) {
    SourceEnv.log("info", "pages: " + url);
    return [];
  }
}
