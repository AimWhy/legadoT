var source = {
  bookSourceUrl: "https://example.com",
  bookSourceName: "示例JS源",
  bookSourceType: 0,
  bookSourceGroup: "",
  bookSourceComment: "纯JS单文件书源:顶层只放 source 配置与函数声明",
  lastUpdateTime: 0
}

function search(key, page) {
  var html = java.ajax(source.bookSourceUrl + "/search?q=" + encodeURI(key) + "&p=" + page)
  var list = []
  // 同步解析 html,逐条 push:
  // list.push({ name: "书名", author: "作者", bookUrl: "https://.../book/1",
  //   coverUrl: "", intro: "", kind: "", wordCount: "", latestChapterTitle: "", tocUrl: "" })
  return list
}

function getBookInfo(book) {
  var html = java.ajax(book.bookUrl)
  return { intro: "", coverUrl: "", latestChapterTitle: "", tocUrl: book.bookUrl }
}

function getChapters(book) {
  var html = java.ajax(book.tocUrl)
  var chapters = []
  // chapters.push({ title: "第1章", url: "https://.../read/1" })
  return chapters
}

function getContent(chapter, book) {
  var html = java.ajax(chapter.url)
  java.log("正文长度: " + html.length)
  return html
}
