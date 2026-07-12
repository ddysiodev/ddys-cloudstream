# DDYS CloudStream

DDYS CloudStream 是低端影视 API 的官方 CloudStream 扩展仓库，面向 Android 和 Android TV 用户，提供首页推荐、分类、搜索、详情页、剧集/线路、播放链接解析和可配置 API Base。

## 功能

- CloudStream repository：`repo.json`
- DDYS Kotlin Provider：`DdysProvider`
- 首页：最新更新、热门内容、电影、剧集、动漫、综艺、纪录片
- 搜索：关键词搜索和分页
- 分类：按 DDYS API `type` 读取分页内容
- 详情：标题、海报、背景图、年份、简介、标签、相关推荐
- 播放：直链、M3U8、MPD、磁力和可被 CloudStream extractor 处理的外部链接
- 剧集/动漫/综艺：把资源列表展示为可选 episode
- 电影：播放时聚合全部可用线路
- 设置页：API Base、Site Base、API Key、首页数量、每页数量、直链策略、外部资源策略
- GitHub Actions 自动构建 `.cs3`、`.jar` 和 `plugins.json`
- 本地静态自检、Node 测试、Release ZIP

## 安装

在 CloudStream 中添加扩展仓库：

```text
https://raw.githubusercontent.com/ddysiodev/ddys-cloudstream/main/repo.json
```

构建产物会发布到：

```text
https://raw.githubusercontent.com/ddysiodev/ddys-cloudstream/builds/plugins.json
```

## 设置

安装扩展后打开插件设置，可以配置：

- `API Base`：默认 `https://ddys.io/api/v1`
- `Site Base`：默认 `https://ddys.io`
- `API Key`：可选，公开读取接口默认不需要
- `每页数量`：默认 24，范围 1-80
- `首页数量`：默认 24，范围 1-80
- `只返回直链资源`
- `包含外部/网盘/磁力资源`

如果你部署了 DDYS Worker Proxy，可以把 `API Base` 改成自己的代理地址。

## 文件结构

```text
ddys-cloudstream/
├── repo.json
├── assets/icon.png
├── DdysProvider/
│   ├── build.gradle.kts
│   └── src/main/kotlin/io/ddys/cloudstream/
├── docs/
├── examples/
├── tests/
└── tools/
```

## 本地检查

```bash
node tools/check.mjs
node --test tests/*.test.mjs
```

Android 编译由 GitHub Actions 执行；本机没有 JDK/Android SDK 时仍可运行静态检查。

## License

MIT
