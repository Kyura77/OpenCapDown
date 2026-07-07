export default {
  id: "verdinha",
  name: "Verdinha",
  lang: "pt-BR",
  baseUrl: "https://verdinha.wtf",
  apiUrl: "https://api.verdinha.wtf",
  cdnUrl: "https://cdn.verdinha.wtf",
  cdnApiUrl: "https://api.verdinha.wtf/cdn",
  scanId: "1",

  search: function(query) {
    var url = this.apiUrl + "/obras/search?obr_nome=" + encodeURIComponent(query) + "&limite=26&pagina=1&todos_generos=1";
    var res = SourceEnv.fetch(url);
    var json = JSON.parse(res);
    var obras = json.obras || [];
    return obras.map(function(m) {
      var img = "";
      if (m.obr_imagem) {
        img = this.cdnUrl + "/scans/" + (m.scan_id || 1) + "/obras/" + m.obr_id + "/" + m.obr_imagem;
      }
      return {
        title: m.obr_nome || m.nome || m.name,
        coverUrl: img,
        url: m.obr_id + ""
      };
    }.bind(this));
  },

  getMangaDetail: function(url) {
    var mangaId = url;
    var res = SourceEnv.fetch(this.apiUrl + "/obras/" + mangaId);
    var json = JSON.parse(res);
    var data = json;
    var img = "";
    if (data.obr_imagem) {
      img = this.cdnUrl + "/scans/" + (data.scan_id || 1) + "/obras/" + data.obr_id + "/" + data.obr_imagem;
    }
    var caps = (data.capitulos || data.chapters || []).map(function(c) {
      return {
        id: c.cap_id + "",
        title: c.cap_nome || "Cap. " + (c.cap_numero || 0),
        url: c.cap_id + "",
        number: parseFloat(c.cap_numero) || 0
      };
    });
    caps.sort(function(a, b) { return b.number - a.number; });
    return {
      title: data.obr_nome || data.nome,
      coverUrl: img,
      description: data.obr_descricao || "",
      status: data.status ? data.status.stt_nome || data.status.name || "" : "",
      chapters: caps
    };
  },

  getChapterPages: function(url) {
    var chapterId = url;
    var res = SourceEnv.fetch(this.apiUrl + "/capitulos/" + chapterId);
    var json = JSON.parse(res);
    var paginas = json.cap_paginas || json.pages || [];
    var obraId = json.obra ? json.obra.obr_id : 0;
    var scanId2 = json.obra ? json.obra.scan_id : 1;
    var capNum = (json.cap_numero || "0").toString().replace(/\.0$/, "");
    return paginas.map(function(p, i) {
      var imageUrl = "";
      var src = p.src || p.imageUrl || "";
      if (src.startsWith("http")) {
        imageUrl = src;
      } else if (src.startsWith("uploads/") || src.startsWith("wp-")) {
        imageUrl = this.baseUrl + "/wp-content/" + src;
      } else {
        imageUrl = this.cdnUrl + "/scans/" + scanId2 + "/obras/" + obraId + "/capitulos/" + capNum + "/" + src;
      }
      return { index: i, imageUrl: imageUrl, headers: {} };
    }.bind(this));
  }
};