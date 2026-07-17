/**
 * @file 纯 JavaScript 单文件书源模板
 *
 * 一个顶层 source 配置对象和若干函数声明组成完整书源。
 * search、getChapters、getContent 必须实现；getBookInfo 可选。
 * source.exploreUrl 与 explore 必须成对使用。
 *
 * 运行时约束：
 * - 可直接使用 java、source、cookie、cache、baseUrl 等绑定对象。网络请求使用同步 API，
 *   例如 java.ajax(url)；调试输出使用 java.log(msg)。
 * - 每次函数调用都在新的脚本作用域执行。需要跨请求保存状态时，使用 cache.put/get
 *   或 source.setVariable/getVariable。
 * - 支持常用 ES6 语法，包括 let/const、箭头函数、模板字符串、for-of、解构、默认参数、
 *   展开运算符、可选链和空值合并；不支持 class、async/await、Promise 回调、函数展开调用
 *   和剩余解构。
 * - book/chapter 属性、java.ajax 和 Jsoup 的返回值可能是 Java 字符串对象。使用字符串方法
 *   或进行判空前，先用 String(value) 转换。
 * - 完整 API（http、webView、编解码和加解密等）请参阅应用内帮助。
 */

/**
 * 书源配置。字段名与 BookSource 实体一致。
 * 常用可选字段包括 header（JSON 格式请求头）、loginUrl、concurrentRate、
 * enabledCookieJar 和 jsLib。enabled、customOrder 等用户态字段由应用维护。
 */
const source = {
  bookSourceUrl: "https://example.com", // 必填；主键，修改后视为新源
  bookSourceName: "示例JS源",           // 必填
  bookSourceType: 0,                    // 0 文本，1 音频，2 图片，3 下载
  bookSourceGroup: "",
  bookSourceComment: "",
  exploreUrl: "",                       // 发现分类；每行一个“名称::url”，由 explore 抓取
  lastUpdateTime: 0                     // 版本时间戳（毫秒）；导入值较新时提示更新
}

/**
 * search/explore 返回的书籍条目。name 和 bookUrl 必填，缺失的条目会被丢弃。
 * bookUrl 和 tocUrl 应返回绝对地址；origin 系字段由应用注入。
 * @typedef {Object} SearchBook
 * @property {string} name
 * @property {string} bookUrl 详情页地址，必须为绝对地址
 * @property {string} [author]
 * @property {string} [kind] 分类，多个分类用逗号分隔
 * @property {string} [coverUrl]
 * @property {string} [intro]
 * @property {string} [wordCount] 字数，例如 "36万字"
 * @property {string} [latestChapterTitle]
 * @property {string} [tocUrl] 目录地址；缺省使用 bookUrl
 * @property {number} [type] 类型位值：文本 8、音频 32、图片 64、下载 128
 */

/**
 * getBookInfo 返回的书籍字段补丁。仅写出的白名单字段会覆盖原值。
 * @typedef {Object} BookInfoPatch
 * @property {string} [name]
 * @property {string} [author]
 * @property {string} [intro]
 * @property {string} [coverUrl]
 * @property {string} [kind]
 * @property {string} [wordCount]
 * @property {string} [latestChapterTitle]
 * @property {string} [tocUrl] 目录地址；缺省使用 book.bookUrl
 * @property {number} [type] 类型位值，同 SearchBook.type
 * @property {string} [variable] JSON 字符串
 */

/**
 * getChapters 返回的目录条目。title 和 url 必填，缺失的条目会被丢弃。
 * 相对 url 会以 book.tocUrl 为基准补全；分卷条目需设置 isVolume: true，且 url 与 title 相同。
 * @typedef {Object} Chapter
 * @property {string} title
 * @property {string} url
 * @property {boolean} [isVolume]
 * @property {boolean} [isVip]
 * @property {boolean} [isPay]
 * @property {string} [wordCount] 字数，例如 "3210字"
 * @property {string} [tag] 更新时间等附加信息
 * @property {string} [resourceUrl] 音频资源地址
 */

/**
 * 搜索书籍。
 * @param {string} key 搜索关键词
 * @param {number} page 页码，从 1 开始
 * @returns {SearchBook[]|string} 书籍数组或 JSON 字符串
 */
function search(key, page) {
  const html = java.ajax(`${source.bookSourceUrl}/search?q=${encodeURI(key)}&p=${page}`)
  const list = []
  // list.push({ name: "书名", bookUrl: "https://example.com/book/1", author: "作者" })
  return list
}

/**
 * 获取发现页书籍，与 source.exploreUrl 成对使用。
 * @param {string} url exploreUrl 中的分类地址，原样传入
 * @param {number} page 页码，从 1 开始；翻页参数由函数自行拼接
 * @returns {SearchBook[]|string} 书籍数组或 JSON 字符串
 */
function explore(url, page) {
  const html = java.ajax(url)
  const list = []
  return list
}

/**
 * 获取书籍详情。可选；未实现时沿用 search 阶段的字段。
 * @param {Object} book 搜索结果中的书籍对象
 * @returns {BookInfoPatch|Object|string} 书籍字段补丁或其 JSON 字符串
 */
function getBookInfo(book) {
  const html = java.ajax(book.bookUrl)
  return { intro: "", coverUrl: "", latestChapterTitle: "", tocUrl: book.bookUrl }
}

/**
 * 获取目录。
 * @param {Object} book 书籍对象
 * @returns {Chapter[]|string} 目录数组或 JSON 字符串，数组顺序即目录顺序
 */
function getChapters(book) {
  const html = java.ajax(book.tocUrl)
  const chapters = []
  // chapters.push({ title: "第一卷", url: "第一卷", isVolume: true })
  // chapters.push({ title: "第1章", url: "https://example.com/read/1" })
  return chapters
}

/**
 * 获取正文。运行时额外提供 nextChapterUrl，表示下一章地址，可能为 null。
 * @param {Object} chapter 章节对象
 * @param {Object} book 书籍对象
 * @returns {string} 正文文本；返回空字符串视为失败。纯文本段落用 \n 分隔；
 *   也可将正文 HTML 传入 java.htmlFormat(html, chapter.url) 转换为文本并保留插图
 */
function getContent(chapter, book) {
  const html = java.ajax(chapter.url)
  // return java.htmlFormat(org.jsoup.Jsoup.parse(html).select("div.content").html(), chapter.url)
  return html
}
