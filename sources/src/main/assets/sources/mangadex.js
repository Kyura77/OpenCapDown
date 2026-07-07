export default {
  id: "mangadex",
  name: "MangaDex",
  lang: "multi",
  baseUrl: "https://api.mangadex.org",

  async search(query) {
    const res = SourceEnv.fetch(`${this.baseUrl}/manga?title=${encodeURIComponent(query)}&limit=10`);
    const json = JSON.parse(res);
    return json.data.map(m => ({
      title: m.attributes.title.en || Object.values(m.attributes.title)[0],
      coverUrl: "",
      url: `${this.baseUrl}/manga/${m.id}`
    }));
  },

  async getMangaDetail(url) {
    const id = url.split("/").pop();
    const res = SourceEnv.fetch(`${this.baseUrl}/manga/${id}?includes[]=cover_art`);
    const json = JSON.parse(res);
    return {
      title: json.data.attributes.title.en,
      coverUrl: "",
      description: json.data.attributes.description.en || "",
      status: json.data.attributes.status || "",
      chapters: []
    };
  },

  async getChapterPages(url) {
    return [];
  }
}
