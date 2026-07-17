/**
 * @file 纯JS单文件书源模板
 *
 * 一个 .js 文件即一个完整书源:顶层为一个 source 配置对象与若干 function 声明。
 * search / getChapters / getContent 必备,getBookInfo 可选;
 * explore 与 source.exploreUrl 成对,声明分类即须实现,启用发现页。
 *
 * 运行环境:
 * - 绑定 java / source / cookie / cache / baseUrl;java.ajax(url) 同步取网页,
 *   java.post/get/webView、加解密(含 CryptoJS)等完整清单见编辑器菜单-帮助。
 * - 全部同步写法,并发由应用调度;每次调用在新 scope 执行,顶层变量不跨调用保留,
 *   跨请求状态存 cache.put/get 或 source.setVariable/getVariable。
 * - 引擎 Rhino:let/const、箭头函数、模板字符串、for-of、解构、默认参数、
 *   字面量展开 [...arr]/{...obj}、?.、?? 可用;class、async/await、Promise 回调、
 *   调用处展开 f(...arr)、剩余解构不可用。
 * - java.log(msg) 输出到书源调试日志。
 * - book/chapter 属性、Jsoup 取值、java.ajax() 返回等 Java 来源字符串:拼接、传参、
 *   作返回字段值直接用;调 JS 字符串方法(正则 replace/match 等)与判空前先 String(...) 归一化。
 */

/**
 * 书源配置。键名与书源实体字段逐字对应,其余常用键:
 * - header:请求头,JSON 字符串,如 '{"User-Agent": "Mozilla/5.0 ..."}'
 * - loginUrl:登录地址,填写后源列表菜单出现"登录"入口
 * - concurrentRate:并发率,如 "1" 或 "3/1000"(次数/毫秒)
 * - enabledCookieJar:是否启用 CookieJar
 * - jsLib:本源所有函数共用的公共 JS 库文本
 * enabled/customOrder 等用户态字段由应用维护,脚本值不生效。
 */
const source = {
  bookSourceUrl: "https://example.com", // 必填,源唯一身份;修改即成为新源
  bookSourceName: "示例JS源",           // 必填
  bookSourceType: 0,                    // 0文本 1音频 2图片 3下载站
  bookSourceGroup: "",
  bookSourceComment: "",
  exploreUrl: "",                       // 发现分类,"名称::url" 每行一个(如 "玄幻::/sort/1\n完本::/finish");填写后由 explore 函数抓取
  lastUpdateTime: 0                     // 版本时间戳,同 URL 重复导入按此判断新旧
}

/**
 * search 返回条目。name/bookUrl 必填,缺失的条目被丢弃;origin 系字段由应用注入。
 *
 * @typedef {Object} SearchBook
 * @property {string} name 书名
 * @property {string} bookUrl 详情页地址
 * @property {string} [author]
 * @property {string} [kind] 分类,逗号分隔,详情页显示为标签
 * @property {string} [coverUrl]
 * @property {string} [intro]
 * @property {string} [wordCount] 如 "36万字"
 * @property {string} [latestChapterTitle]
 * @property {string} [tocUrl] 缺省用 bookUrl
 * @property {number} [type] BookType 位值:文本8 音频32 图片64 下载128
 */

/**
 * getBookInfo 返回值,按键覆盖书籍字段,仅以下键生效。
 *
 * @typedef {Object} BookInfoPatch
 * @property {string} [name]
 * @property {string} [author]
 * @property {string} [intro]
 * @property {string} [coverUrl]
 * @property {string} [kind]
 * @property {string} [wordCount]
 * @property {string} [latestChapterTitle]
 * @property {string} [tocUrl] 缺省用 book.bookUrl
 * @property {number} [type] 同 SearchBook.type
 * @property {string} [variable] JSON 字符串,如 '{"k":"v"}'
 */

/**
 * getChapters 返回条目。title/url 必填,缺失的条目被丢弃;相对 url 按 book.tocUrl 补全。
 * 分卷行:isVolume 为 true 且 url 与 title 相同,不抓正文。
 *
 * @typedef {Object} Chapter
 * @property {string} title 章节名
 * @property {string} url 章节地址
 * @property {boolean} [isVolume] 分卷行
 * @property {boolean} [isVip]
 * @property {boolean} [isPay]
 * @property {string} [wordCount] 如 "3210字"
 * @property {string} [tag] 更新时间等附加说明
 * @property {string} [resourceUrl] 音频真实地址
 */

/**
 * 搜索。
 *
 * @param {string} key 搜索词
 * @param {number} page 页码,从 1 起
 * @returns {SearchBook[]} 返回数组或 JSON 字符串均可,下同
 */
function search(key, page) {
  const html = java.ajax(`${source.bookSourceUrl}/search?q=${encodeURI(key)}&p=${page}`)
  const list = []
  // list.push({ name: "书名", bookUrl: "https://.../book/1", author: "作者" })
  return list
}

/**
 * 发现,与 source.exploreUrl 成对(声明分类则必须实现)。
 *
 * @param {string} url 当前分类的地址(exploreUrl 条目的 url 段),原样传入;翻页用 page 自行拼接
 * @param {number} page 页码,从 1 起
 * @returns {SearchBook[]} 契约同 search
 */
function explore(url, page) {
  const html = java.ajax(url)
  const list = []
  // list.push({ name: "书名", bookUrl: "https://.../book/1", author: "作者" })
  return list
}

/**
 * 详情,可选;缺省沿用 search 阶段字段。
 *
 * @param {Object} book 书籍对象
 * @returns {BookInfoPatch}
 */
function getBookInfo(book) {
  const html = java.ajax(book.bookUrl)
  return { intro: "", coverUrl: "", latestChapterTitle: "", tocUrl: book.bookUrl }
}

/**
 * 目录。
 *
 * @param {Object} book 书籍对象
 * @returns {Chapter[]} 数组顺序即目录顺序
 */
function getChapters(book) {
  const html = java.ajax(book.tocUrl)
  const chapters = []
  // chapters.push({ title: "第一卷", url: "第一卷", isVolume: true })
  // chapters.push({ title: "第1章", url: "https://.../read/1" })
  return chapters
}

/**
 * 正文。环境另绑定 nextChapterUrl:下一章地址,可能为 null。
 *
 * @param {Object} chapter 章节对象
 * @param {Object} book 书籍对象
 * @returns {string} 正文;空视为失败
 */
function getContent(chapter, book) {
  const html = java.ajax(chapter.url)
  return html
}
