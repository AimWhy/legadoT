# js变量和函数
> 阅读使用[htmlunit-core-js](https://github.com/HtmlUnit/htmlunit-core-js) 作为JavaScript引擎以便于[调用Java类和方法](https://m.jb51.net/article/92138.htm)，查看[ECMAScript兼容性表格](https://mozilla.github.io/rhino/compat/engines.html)
> [Rhino运行时](https://github.com/HtmlUnit/htmlunit-core-js/blob/master/src/repackaged-rhino/java/org/htmlunit/corejs/javascript/ScriptRuntime.java)懒加载导入的Java类和方法

|构造函数|函数|对象|调用类|简要说明|
|------|-----|------|----|------|
|JavaImporter|importClass importPackage| |[ImporterTopLevel](https://github.com/HtmlUnit/htmlunit-core-js/blob/master/src/repackaged-rhino/java/org/htmlunit/corejs/javascript/ImporterTopLevel.java)|导入Java类到JavaScript|
||getClass|Packages java javax ...|[NativeJavaTopPackage](https://github.com/HtmlUnit/htmlunit-core-js/blob/master/src/repackaged-rhino/java/org/htmlunit/corejs/javascript/NativeJavaTopPackage.java)|默认导入JavaScript中的Java类|
|JavaAdapter|||[JavaAdapter](https://github.com/HtmlUnit/htmlunit-core-js/blob/master/src/repackaged-rhino/java/org/htmlunit/corejs/javascript/JavaAdapter.java)|继承Java类|

> 注意`java`变量指向已经被阅读修改，如果想要调用`java.*`下的包，请使用`Packages.java.*`

> 在书源规则中使用`@js` `<js>` `{{}}`可使用JavaScript调用阅读部分内置的类和方法

> 注意为了安全，阅读会屏蔽部分java类调用，见[RhinoClassShutter](https://github.com/gedoor/legado/blob/master/modules/rhino/src/main/java/com/script/rhino/RhinoClassShutter.kt)

> 不同的书源规则中支持的调用的Java类和方法可能有所不同

|变量名|调用类|
|------|-----|
|java|当前类|
|baseUrl|当前url,String  |
|result|上一步的结果|
|book|[书籍类](https://github.com/gedoor/legado/blob/master/app/src/main/java/io/legado/app/data/entities/Book.kt)|
|rssArticle|[Article类](https://github.com/gedoor/legado/blob/master/app/src/main/java/io/legado/app/data/entities/RssArticle.kt)|
|chapter|[章节类](https://github.com/gedoor/legado/blob/master/app/src/main/java/io/legado/app/data/entities/BookChapter.kt)|
|source|[基础书源类](https://github.com/gedoor/legado/blob/master/app/src/main/java/io/legado/app/data/entities/BaseSource.kt)|
|cookie|[cookie操作类](https://github.com/gedoor/legado/blob/master/app/src/main/java/io/legado/app/help/http/CookieStore.kt)| 
|cache|[缓存操作类](https://github.com/gedoor/legado/blob/master/app/src/main/java/io/legado/app/help/CacheManager.kt)|
|title|章节当前标题 String|
|src| 请求返回的源码|
|nextChapterUrl|下一章节url|

## 当前类对象的可使用的部分方法

### [RssJsExtensions](https://github.com/gedoor/legado/blob/master/app/src/main/java/io/legado/app/ui/rss/read/RssJsExtensions.kt)
> 只能在订阅源`shouldOverrideUrlLoading`规则中使用  
> 订阅添加跳转url拦截, js, 返回true拦截,js变量url,可以通过js打开url  
> url跳转拦截规则不能执行耗时操作
> 例子https://github.com/gedoor/legado/discussions/3259

* 调用阅读搜索

```js
java.searchBook(bookName: String)
```

* 添加书架

```js
java.addBook(bookUrl: String)
```

### [AnalyzeUrl](https://github.com/gedoor/legado/blob/master/app/src/main/java/io/legado/app/model/analyzeRule/AnalyzeUrl.kt) 部分函数
> js中通过java.调用,只在`登录检查JS`规则中有效
```js
initUrl() //重新解析url,可以用于登录检测js登录后重新解析url重新访问
getHeaderMap().putAll(source.getHeaderMap(true)) //重新设置登录头
getStrResponse( jsStr: String? = null, sourceRegex: String? = null) //返回访问结果,文本类型,书源内部重新登录后可调用此方法重新返回结果
getResponse(): Response //返回访问结果,网络朗读引擎采用的是这个,调用登录后在调用这方法可以重新访问,参考阿里云登录检测
```

### [AnalyzeRule](https://github.com/gedoor/legado/blob/master/app/src/main/java/io/legado/app/model/analyzeRule/AnalyzeRule.kt) 部分函数
* 获取文本/文本列表
> `mContent` 待解析源代码，默认为当前页面  
> `isUrl` 链接标识，默认为`false`
```js
java.getString(ruleStr: String?, mContent: Any? = null, isUrl: Boolean = false)
java.getStringList(ruleStr: String?, mContent: Any? = null, isUrl: Boolean = false)
```
* 设置解析内容

```js
java.setContent(content: Any?, baseUrl: String? = null):
```

* 获取Element/Element列表

> 如果要改变解析源代码，请先使用`java.setContent`

```js
java.getElement(ruleStr: String)
java.getElements(ruleStr: String)
```

* 重新搜索书籍/重新获取目录url

> 只能在刷新目录之前使用,有些书源书籍地址和目录url会变

```js
java.reGetBook()
java.refreshTocUrl()
```
* 变量存取

```js
java.get(key)
java.put(key, value)
```

* 并发合并(single-flight)

> 同一 name 并发时只有一个线程跑 action,其余等它完成后跳过、自行读结果;action 失败由下个线程重试,等待超 timeoutMs(默认 15000)抛异常。
> jsLib 里的函数如需调用java/source等对象需绑定this: fn.bind(this)。

```js
java.singleFlight(name: String, action: Function, timeoutMs: Long = 15000)
```

* 互斥锁(串行化)

> 同一 name 并发时逐个排队、每个都执行(与 single-flight 跳过相反),把整段读-改-写包进 action 避免并发丢失更新;超时与 this 绑定规则同 single-flight。

```js
java.lock(name: String, action: Function, timeoutMs: Long = 15000)
```

* 轮询计数器

> 进程内原子自增计数器,返回非负序号,同 name 跨线程/执行共享;重启归零。

```js
java.tick(name: String): Int
```

### [js扩展类](https://github.com/gedoor/legado/blob/master/app/src/main/java/io/legado/app/help/JsExtensions.kt) 部分函数

* 链接解析[JsURL](https://github.com/gedoor/legado/blob/master/app/src/main/java/io/legado/app/utils/JsURL.kt)
```js
java.toURL(url): JsURL
java.toURL(url, baseUrl): JsURL
```
* 获取SystemWebView User-Agent
```js
java.getWebViewUA(): String
```
* 网络请求
```js
java.ajax(urlStr): String
java.ajaxAll(urlList: Array<String>): Array<StrResponse>
//返回StrResponse 方法body() code() message() headers() raw() toString() 
java.connect(urlStr): StrResponse

java.post(url: String, body: String, headerMap: Map<String, String>): Connection.Response
java.post(url: String, body: String, headerJson: String?): Connection.Response

java.get(url: String, headerMap: Map<String, String>): Connection.Response
java.get(url: String, headerJson: String?): Connection.Response

java.head(url: String, headerMap: Map<String, String>): Connection.Response
java.head(url: String, headerJson: String?): Connection.Response

* 使用webView访问网络
* @param html 直接用webView载入的html, 如果html为空直接访问url
* @param url html内如果有相对路径的资源不传入url访问不了
* @param js 用来取返回值的js语句, 没有就返回整个源代码
* @return 返回js获取的内容
java.webView(html: String?, url: String?, js: String?): String?

* 使用webView获取跳转url
java.webViewGetOverrideUrl(html: String?, url: String?, js: String?, overrideUrlRegex: String): String?

* 使用webView获取资源url
java.webViewGetSource(html: String?, url: String?, js: String?, sourceRegex: String): String?

* 使用内置浏览器打开链接，可用于获取验证码 手动验证网站防爬
* @param url 要打开的链接
* @param title 浏览器的标题
java.startBrowser(url: String, title: String)

* 使用内置浏览器打开链接，并等待网页结果 .body()获取网页内容
java.startBrowserAwait(url: String, title: String, refetchAfterSuccess: Boolean? = true): StrResponse
```
* 调试
```js
java.log(msg)
java.logType(var)
```
* 获取用户输入的验证码
```js
java.getVerificationCode(imageUrl)
```
* 弹窗提示
```js
java.longToast(msg: Any?)
java.toast(msg: Any?)
```
* 从网络(由java.cacheFile实现)、本地读取JavaScript文件，导入上下文请手动`eval(String(...))`
```js
java.importScript(url)
//相对路径支持android/data/{package}/cache
java.importScript(relativePath)
java.importScript(absolutePath)
```
* 缓存网络文件
```js
获取
java.cacheFile(url)
java.cacheFile(url,saveTime)
执行内容
eval(String(java.cacheFile(url)))
使缓存失效
cache.delete(java.md5Encode16(url))
```
* 获取网络压缩文件里面指定路径的数据 *可替换Zip Rar 7Z
```js
java.get*StringContent(url: String, path: String): String

java.get*StringContent(url: String, path: String, charsetName: String): String

java.get*ByteArrayContent(url: String, path: String): ByteArray?

```
* URI编码
```js
java.encodeURI(str: String) //默认enc="UTF-8"
java.encodeURI(str: String, enc: String)
```
* base64
> flags参数可省略，默认Base64.NO_WRAP，查看[flags参数说明](https://blog.csdn.net/zcmain/article/details/97051870)
```js
java.base64Decode(str: String)
java.base64Decode(str: String, charset: String)
java.base64DecodeToByteArray(str: String, flags: Int)
java.base64Encode(str: String, flags: Int)
```
* ByteArray
```js
Str转Bytes
java.strToBytes(str: String)
java.strToBytes(str: String, charset: String)
Bytes转Str
java.bytesToStr(bytes: ByteArray)
java.bytesToStr(bytes: ByteArray, charset: String)
```
* Hex
```js
HexString 解码为字节数组
java.hexDecodeToByteArray(hex: String)
hexString 解码为utf8String
java.hexDecodeToString(hex: String)
utf8 编码为hexString
java.hexEncodeToString(utf8: String)
```
* 标识id
```js
java.randomUUID()
java.androidId()
```
* 繁简转换
```js
将文本转换为简体
java.t2s(text: String): String
将文本转换为繁体
java.s2t(text: String): String
```
* 时间格式化
```js
java.timeFormatUTC(time: Long, format: String, sh: Int): String?
java.timeFormat(time: Long): String
```
* html格式化
```js
java.htmlFormat(str: String): String
```
* 文件
>  所有对于文件的读写删操作都是相对路径,只能操作阅读缓存/android/data/{package}/cache/内的文件
```js
//文件下载 url用于生成文件名，返回文件路径
downloadFile(url: String): String
//文件解压,zipPath为压缩文件路径，返回解压路径
unArchiveFile(zipPath: String): String
unzipFile(zipPath: String): String
unrarFile(zipPath: String): String
un7zFile(zipPath: String): String
//文件夹内所有文件读取
getTxtInFolder(unzipPath: String): String
//读取文本文件
readTxtFile(path: String): String
//删除文件
deleteFile(path: String) 
```

### [js加解密类](https://github.com/gedoor/legado/blob/master/app/src/main/java/io/legado/app/help/JsEncodeUtils.kt) 部分函数

> 规则中可直接使用 `CryptoJS`（如 `CryptoJS.MD5(...)`），也可按下方方法使用 `java.*` 加解密函数。

> 提供在JavaScript环境中快捷调用crypto算法的函数，由[hutool-crypto](https://www.hutool.cn/docs/#/crypto/概述)实现  
> 由于兼容性问题，hutool-crypto当前版本为5.8.22  

> 注意：如果输入的参数不是Utf8String 可先调用`java.hexDecodeToByteArray java.base64DecodeToByteArray`转成ByteArray
* 对称加密
> 输入参数key iv 支持ByteArray|**Utf8String**
```js
// 创建Cipher
java.createSymmetricCrypto(transformation, key, iv)
```
>解密加密参数 data支持ByteArray|Base64String|HexString|InputStream
```js
//解密为ByteArray String
cipher.decrypt(data)
cipher.decryptStr(data)
//加密为ByteArray Base64字符 HEX字符
cipher.encrypt(data)
cipher.encryptBase64(data)
cipher.encryptHex(data)
```
* 非对称加密
> 输入参数 key支持ByteArray|**Utf8String**
```js
//创建cipher
java.createAsymmetricCrypto(transformation)
//设置密钥
.setPublicKey(key)
.setPrivateKey(key)

```
> 解密加密参数 data支持ByteArray|Base64String|HexString|InputStream  
```js
//解密为ByteArray String
cipher.decrypt(data,  usePublicKey: Boolean? = true
)
cipher.decryptStr(data, usePublicKey: Boolean? = true
)
//加密为ByteArray Base64字符 HEX字符
cipher.encrypt(data,  usePublicKey: Boolean? = true
)
cipher.encryptBase64(data,  usePublicKey: Boolean? = true
)
cipher.encryptHex(data,  usePublicKey: Boolean? = true
)
```
* 签名
> 输入参数 key 支持ByteArray|**Utf8String**
```js
//创建Sign
java.createSign(algorithm)
//设置密钥
.setPublicKey(key)
.setPrivateKey(key)
```
> 签名参数 data支持ByteArray|inputStream|String
```js
//签名输出 ByteArray HexString
sign.sign(data)
sign.signHex(data)
```
* 摘要
```js
java.digestHex(data: String, algorithm: String,): String?

java.digestBase64Str(data: String, algorithm: String,): String?
```
* md5
```js
java.md5Encode(str)
java.md5Encode16(str)
```
* HMac
```js
java.HMacHex(data: String, algorithm: String, key: String): String

java.HMacBase64(data: String, algorithm: String, key: String): String
```

## book对象的可用属性
### 属性
> 使用方法: 在js中或{{}}中使用book.属性的方式即可获取.如在正文内容后加上 ##{{book.name+"正文卷"+title}} 可以净化 书名+正文卷+章节名称（如 我是大明星正文卷第二章我爸是豪门总裁） 这一类的字符.
```js
bookUrl // 详情页Url(本地书源存储完整文件路径)
tocUrl // 目录页Url (toc=table of Contents)
origin // 书源URL(默认BookType.local)
originName //书源名称 or 本地书籍文件名
name // 书籍名称(书源获取)
author // 作者名称(书源获取)
kind // 分类信息(书源获取)
customTag // 分类信息(用户修改)
coverUrl // 封面Url(书源获取)
customCoverUrl // 封面Url(用户修改)
intro // 简介内容(书源获取)
customIntro // 简介内容(用户修改)
charset // 自定义字符集名称(仅适用于本地书籍)
type // 0:text 1:audio
group // 自定义分组索引号
latestChapterTitle // 最新章节标题
latestChapterTime // 最新章节标题更新时间
lastCheckTime // 最近一次更新书籍信息的时间
lastCheckCount // 最近一次发现新章节的数量
totalChapterNum // 书籍目录总数
durChapterTitle // 当前章节名称
durChapterIndex // 当前章节索引
durChapterPos // 当前阅读的进度(首行字符的索引位置)
durChapterTime // 最近一次阅读书籍的时间(打开正文的时间)
canUpdate // 刷新书架时更新书籍信息
order // 手动排序
originOrder //书源排序
variable // 自定义书籍变量信息(用于书源规则检索书籍信息)
 ```

## chapter对象的部分可用属性
> 使用方法: 在js中或{{}}中使用chapter.属性的方式即可获取.如在正文内容后加上 ##{{chapter.title+chapter.index}} 可以净化 章节标题+序号(如 第二章 天仙下凡2) 这一类的字符.
 ```js
 url // 章节地址
 title // 章节标题
 baseUrl //用来拼接相对url
 bookUrl // 书籍地址
 index // 章节序号
 resourceUrl // 音频真实URL
 tag //
 start // 章节起始位置
 end // 章节终止位置
 variable //变量
 ```
 
## source对象的部分可用函数
* 获取书源url
```js
source.getKey()
```
* 书源变量存取
```js
source.setVariable(variable: String?)
source.getVariable()
```

* 登录头操作
```js
获取登录头
source.getLoginHeader()
获取登录头某一键值
source.getLoginHeaderMap().get(key: String)
保存登录头
source.putLoginHeader(header: String)
清除登录头
source.removeLoginHeader()
```
* 用户登录信息操作
> 使用`登录UI`规则，并成功登录，阅读自动加密保存登录UI规则中除type为button的信息
```js
login函数获取登录信息
source.getLoginInfo()
login函数获取登录信息键值
source.getLoginInfoMap().get(key: String)
清除登录信息
source.removeLoginInfo()
```
## cookie对象的部分可用函数
```js
获取全部cookie
cookie.getCookie(url)
获取cookie某一键值
cookie.getKey(url,key)
设置cookie
cookie.setCookie(url,cookie)
替换cookie
cookie.replaceCookie(url,cookie)
删除cookie
cookie.removeCookie(url)
```

## cache对象的部分可用函数
> saveTime单位:秒，可省略  
> 保存至数据库和缓存文件(50M)，保存的内容较大时请使用`getFile putFile`
```js
保存
cache.put(key: String, value: String, saveTime: Int)
读取数据库
cache.get(key: String): String?
删除
cache.delete(key: String)
缓存文件内容
cache.putFile(key: String, value: String, saveTime: Int)
读取文件内容
cache.getFile(key: String): String?
保存到内存
cache.putMemory(key: String, value: Any)
读取内存
cache.getFromMemory(key: String): Any?
删除内存
cache.deleteMemory(key: String)
```

## 跳转外部链接/应用函数
```js
// 跳转外部链接，传入http链接或者scheme跳转到浏览器或其他应用
java.openUrl(url:String)
// 指定mimeType，可以跳转指定类型应用，例如（video/*）
java.openUrl(url:String,mimeType:String)
```

## 纯JS单文件书源

> 与上面"书源规则中嵌入 `<js>`/`{{}}`"不同：这是另一种书源形态——**一个 `.js` 文件就是一个完整书源**，
> 不写 XPath/JSONPath/CSS 规则，搜索/详情/目录/正文四步全部自己写 JS 抓取并 `return` 数据。
> 管理页"新建JS源"直接给出模板；已有 `mainJs` 的书源点编辑会自动进整页代码编辑器。

### 文件结构

顶层只放两类声明：一个 `source` 配置对象，若干个 `function` 声明。声明即导出，不需要
`export`；也不需要在别处注册，函数名固定、由应用按名调用。

```js
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
  // list.push({ name: "书名", author: "作者", bookUrl: "https://.../book/1", ... })
  return list
}

function getChapters(book) {
  var html = java.ajax(book.tocUrl)
  var chapters = []
  // chapters.push({ title: "第1章", url: "https://.../read/1" })
  return chapters
}

function getContent(chapter, book) {
  var html = java.ajax(chapter.url)
  return html
}
```

`search`、`getChapters`、`getContent` 三个函数必备，缺一在导入/保存时即报错（形如
"JS源缺少必备函数 getContent"）；`getBookInfo` 可选，不写就跳过、只用 `search` 阶段给出的字段。
本形态没有"发现"和"登录"入口：校验书源时发现检查会自动跳过 JS 源，不会因为没写而报错。

### source 配置对象

键名与书源实体字段一一对应（逐字、大小写敏感），常用字段：

|键名|说明|
|------|------|
|bookSourceUrl|必填，书源唯一身份，改它等于新建一个源|
|bookSourceName|必填，显示名称|
|bookSourceType|0文本 / 1音频 / 2图片 / 3下载站，决定 `book.type` 缺省值与详情/播放UI|
|bookSourceGroup|分组，可留空|
|bookSourceComment|备注|
|lastUpdateTime|版本时间戳；同 `bookSourceUrl` 重复导入按它判断是否"更新"|
|header|请求头 JSON 字符串，同声明式源|
|loginUrl / loginUi / loginCheckJs|登录相关；填了 loginUrl 后管理列表该源菜单会出现"登录"入口，与声明式源一致|
|concurrentRate|并发限制，同声明式源|
|enabledCookieJar|是否启用 CookieJar|
|jsLib|共享给本源所有函数调用的公共 JS 库文本|

`enabled`、`customOrder`、`weight`、`respondTime` 等用户态/统计字段不受脚本控制——保存时从
数据库里的旧记录继承，脚本里写了也会被忽略。

### 四个函数

|函数|时机|入参|返回|
|------|------|------|------|
|`search(key, page)`|搜索|`key`:搜索词；`page`:页码(从1起)|书籍数组|
|`getBookInfo(book)`|详情，可选|`book`:书籍对象(已含 search 阶段字段)|要覆盖的字段对象|
|`getChapters(book)`|目录|`book`:书籍对象|章节数组|
|`getContent(chapter, book)`|正文|`chapter`:章节对象；`book`:书籍对象；另绑定同名变量 `nextChapterUrl`(下一章地址,可能为 null)|正文字符串|

返回值可以直接 `return` 一个数组/对象，也可以 `return JSON.stringify(...)` 手写好的字符串，
两者等价——引擎收到字符串直接用，收到对象/数组会自动转成 JSON 再解析。

- **`search` 每项**：`name`、`bookUrl` 必填，缺一该条会被丢弃（其余项不受影响）；建议带上
  `author`、`coverUrl`、`intro`、`kind`、`wordCount`、`latestChapterTitle`、`tocUrl`。
  `origin`/`originName`/`originOrder` 由应用注入，不需要也不能在返回值里覆盖。
- **`getBookInfo` 返回对象**：只有写出的键才会覆盖 `book` 对应字段，白名单为 `name`、
  `author`、`intro`、`coverUrl`、`kind`、`wordCount`、`latestChapterTitle`、`tocUrl`、
  `variable`、`type`；其余键（含 `bookUrl` 等主键、`dur*`/`custom*` 用户态字段）一律忽略。
  不写 `tocUrl` 时应用会用 `book.bookUrl` 兜底当目录页。`variable` 的值必须是 **JSON 字符串**
  （如 `"{\"k\":\"v\"}"`），直接写对象字面量会被忽略并记调试日志。
- **`getChapters` 每项**：`title`、`url` 必填，缺一丢弃；可选 `isVolume`、`isVip`、`isPay`、
  `tag`、`wordCount`、`resourceUrl`。相对 `url` 会按 `book.tocUrl` 自动补全成绝对地址。
  卷名行的约定：`isVolume: true` 且 `url` 与 `title` 写成相同字符串——命中这个约定的行点开
  不会尝试抓正文（不会报错，直接展示 `tag` 或空文本）。
- **`type` 覆写**：`search`/`getBookInfo` 返回值里都可以带 `type` 字段，用 BookType 位值：
  文本=8、音频=32、图片=64、只提供下载服务=128；不写或写了非法值时用
  `bookSourceType` 换算出的缺省值，不合法的值会在源调试日志里提示、不会中断抓取。
  `wordCount` 是字符串，不是数字。

### 运行环境

- `java.*` 全量可用：`java.ajax(url)` 同步取网页、`java.post(...)`/`java.get(...)`、
  `java.base64Decode(...)`、`java.log(msg)` 输出到源调试控制台、`CryptoJS.MD5(...)` 等加解密
  方法，见本文上方各节——纯JS单文件源与声明式源里的 `<js>` 共用同一套 `java.*` 能力，
  没有裸的全局 `log(...)`，一律要写 `java.log(...)`。
- `source`、`cookie`、`cache`、`baseUrl` 同名绑定可直接使用；`key`/`page`/`book`/`chapter`/
  `nextChapterUrl` 既是当前函数的形参，也是同名的环境绑定（`jsLib` 里定义的辅助函数如果要用
  这些绑定，需要显式接收对应参数，不能隐式取到调用方的绑定）。
- 并发由应用协程层负责调度，函数按同步写法写就行，不需要自己管线程。但同一个源的多个函数
  可能被并发调用（比如批量搜索），函数内部不要依赖顶层可变状态做跨调用传值（顶层 `var` 当
  只读常量用），需要跨请求持久化的数据存 `cache`（`cache.put/get`）或书源变量
  （`source.setVariable/getVariable`），不要指望进程内全局变量能撑住状态。

### 语法边界

引擎是 Rhino（ES 部分兼容），可以放心用：`function` 声明、`var`/`let`/`const`、`for`/`for-in`、
`if`/`else`/`while`、正则、模板字符串、基础对象/数组字面量。**避免使用**：`class`、
`async`/`await`、`Promise` 链式调用、调用处的展开语法 `f(...arr)`、数组剩余解构
`var [a, ...b] = arr`、`export`/`import`。写法上遇到不确定的新语法，优先按上面模板里的
`function` + `var` 写。

### 导入与分享

- 管理页顶部菜单"新建JS源"：新建空白编辑器，预填模板，改完保存即入库。
- 已有 `mainJs` 的源在管理页点编辑，会自动识别并进入整页代码编辑器（区别于声明式源的分Tab
  表单编辑器）。
- 编辑器菜单"分享"：把当前脚本整篇导出为 `<书源名>.js` 文件分享出去，对方直接导入该文件
  即得到同一个源。
- 支持三种导入方式：粘贴脚本全文、从文件管理器打开 `.js` 文件、填一个 `.js` 直链地址。
- 脚本是配置的唯一真理源：改 `source` 里的字段、保存，立即生效；`enabled`/排序等用户态字段
  不会因为保存而被重置。改 `bookSourceUrl` 相当于新建一个源，旧的那条记录会被删除。
- 编辑器离开页面前，如果内容相对打开时有改动，会弹出"未保存"确认；没改动直接退出，不打扰。
