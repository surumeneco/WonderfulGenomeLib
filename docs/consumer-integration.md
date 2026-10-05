# WGL Consumer Integration Guide

この文書は、Minecraftプラグインから Wonderful Genome Lib (WGL) を利用するための実装入口です。WGL内部実装を探索せず、ここから依存関係、Paper連携、Profile実装、Genome操作、永続化まで辿れることを目的とします。

設計上の正本は Google Drive の `forGPT/XPlayServer/Minecraft/Wonderful Genome Lib.md` と `forGPT/XPlayServer/Minecraft/WGL/` です。この文書は、現在の `develop` 実装に対応する利用手順・公開APIリファレンスです。

## 1. 現在の前提

| 項目 | 現在値 |
| --- | --- |
| Java | 25 |
| Paper API | 26.2 build 129 stable |
| WGL version | `0.1.0-SNAPSHOT` |
| Gradle group | `co.surumene` |
| Paper plugin name | `WonderfulGenomeLib` |
| Consumer compile dependency | `co.surumene:wgl-plugin:0.1.0-SNAPSHOT` |

WGLは現在SNAPSHOT開発中のため、XPlayServer配下のconsumerではGradle Composite Buildを標準とします。正式なartifact repositoryへ公開された後は依存取得方法だけを差し替え、公開APIの利用方法は同じです。

## 2. 開発時の依存取得

WGLとconsumer repositoryを兄弟ディレクトリとしてcloneします。

```text
workspace/
├─ WonderfulGenomeLib/
└─ YourConsumerPlugin/
```

consumerの `settings.gradle.kts`:

```kotlin
rootProject.name = "YourConsumerPlugin"

val wglDirectory = file("../WonderfulGenomeLib")
check(wglDirectory.isDirectory) {
    "WonderfulGenomeLib must be checked out next to YourConsumerPlugin: ${wglDirectory.absolutePath}"
}

includeBuild(wglDirectory)
```

consumerの `build.gradle.kts`:

```kotlin
dependencies {
    compileOnly("co.surumene:wgl-plugin:0.1.0-SNAPSHOT")

    testImplementation("co.surumene:wgl-plugin:0.1.0-SNAPSHOT")
}
```

Profileやconsumer固有ロジックの単体テストでWGL core実装型を直接必要とする場合だけ、テストスコープに追加します。

```kotlin
testImplementation("co.surumene:wgl-core:0.1.0-SNAPSHOT")
```

production codeでは `co.surumene.wgl.core.*` へ直接依存せず、原則として `co.surumene.wgl.api.*` と `WonderfulGenomeLibService` を入口にします。

## 3. Paper実行時の依存

WGLはconsumer JARへ同梱しません。サーバーの `plugins/` には、WGL JARとconsumer JARを別々に配置します。

consumerの `plugin.yml`:

```yaml
depend:
  - WonderfulGenomeLib
```

これによりPaperはWGLをconsumerより先に有効化します。ただしWGLのenable失敗等もあり得るため、service取得結果は必ず検証します。

## 4. Paper serviceの取得

公開入口は次のinterfaceです。

```java
co.surumene.wgl.plugin.WonderfulGenomeLibService
```

典型的な取得:

```java
import co.surumene.wgl.plugin.WonderfulGenomeLibService;
import org.bukkit.Bukkit;

WonderfulGenomeLibService wgl =
        Bukkit.getServicesManager().load(WonderfulGenomeLibService.class);

if (wgl == null) {
    throw new IllegalStateException("WonderfulGenomeLib service is unavailable");
}
```

主なservice API:

| API | 用途 |
| --- | --- |
| `engine()` | 現在有効な `GenomeEngine` を取得 |
| `registerProfile(owner, profile)` | consumer所有Profileを新規登録 |
| `replaceProfile(owner, profile)` | 同じownerの既存Profileを差し替え |
| `profile(profileId)` | 登録Profileを検索 |
| `unregisterOwner(owner)` | ownerが登録したProfileを全解除 |
| `reloadEngineConfiguration()` | WGL共通Engine configを再読込 |

### Engine instanceの扱い

現行実装では `reloadEngineConfiguration()` 成功時に内部の `GenomeEngine` instanceが差し替わります。

そのため、WGL共通config reloadを利用するconsumerは、長寿命オブジェクトへ `service.engine()` の返値を固定保存せず、operation開始時に現在の `service.engine()` を取得する構造を基本とします。古いEngineを保持し続けると、reload後のWGL共通設定がそのconsumerへ反映されません。

## 5. consumerが実装するもの

WGLはGenome engineであり、ゲーム固有pluginの代替ではありません。consumer側で次を所有します。

| consumer所有 | WGL所有 |
| --- | --- |
| `GenomeProfile<P>` の具体実装 | Profile SPI |
| Profile固有configとvalidation/reload | WGL共通Engine config |
| `BackboneDefinition` の定義 | Backboneを使ったFounder scaffold生成 |
| ゲーム上のTarget生成 | TargetからGenomeを合成するSynthesizer |
| Entity identity / UUID / Owner | Genome value object |
| PDC / DB / fileへの保存 | Genome binary codec |
| Phenotype Snapshot | Genome decode結果 |
| 親子・祖父母・世代等のpedigree | Genome由来marker |
| Entityへの能力・AI・UI適用 | decode / breed / mutation / homology |

Profile固有の意味や設定をWGL側へ追加しません。

## 6. GenomeProfileの最小契約

公開SPI:

```java
public interface GenomeProfile<P> {
    ProfileDescriptor descriptor();
    boolean isDefinedAddress(GenomeAddress address);
    DirectContributionModel contributionModel(GenomeAddress address);
    P mapPhenotype(DecodedGenome decodedGenome);
}
```

実際にはFounder variation、homology context、synthesis plan、Profile固有SynthesisBlock等のdefault hookも利用できます。必要になった時点で `GenomeProfile` のJavadocとDriveの `WGL/02_Profile・Synthesizer API.md` を参照してください。

### ProfileDescriptor

```java
new ProfileDescriptor(
    "your-profile-id",
    1,
    semanticFingerprint // exactly 32 bytes
);
```

- `profileId`: `[a-z0-9][a-z0-9._-]{0,63}`
- `profileVersion`: 1以上
- `semanticFingerprint`: Profileのtyped設定値から決定論的に生成する32byte値

Address map、Decoder式、Synthesis semantics等の意味を変更した場合は `profileVersion` を更新します。

Profile instanceはimmutable / thread-safeを前提とします。

## 7. Profile lifecycle

consumer enable時:

```java
GenomeProfile<?> profile = buildImmutableProfile(config);
wgl.registerProfile(this, profile);
```

consumer config reload時:

```java
GenomeProfile<?> replacement = buildImmutableProfile(validatedConfig);
wgl.replaceProfile(this, replacement);
```

consumer disable時:

```java
wgl.unregisterOwner(this);
```

WGL側も `PluginDisableEvent` でconsumer ownerの登録を自動解除します。consumer側の明示解除は、自身のlifecycleを閉じる処理として行って構いません。

registryの規則:

- 同一 `profileId` の二重registerは失敗します。
- `replaceProfile` は既に登録済みで、かつ同じowner pluginのProfileだけ差し替えられます。
- 他pluginが所有する `profileId` は差し替えられません。

## 8. GenomeEngine

入口:

```java
GenomeEngine engine = wgl.engine();
```

公開操作:

```java
<P> DecodeResult<P> decode(GenomeProfile<P> profile, DiploidGenome genome);

SynthesisResult synthesize(
    GenomeProfile<?> profile,
    BackboneDefinition backbone,
    SynthesisTarget target,
    SynthesisContext context,
    GenomeRandom random);

CompatibilityReport assessCompatibility(
    DiploidGenome parentA,
    DiploidGenome parentB,
    CompatibilityPolicy policy);

BreedingResult breed(
    GenomeProfile<?> profile,
    DiploidGenome parentA,
    DiploidGenome parentB,
    BreedingContext context,
    GenomeRandom random);

byte[] encode(DiploidGenome genome);
DiploidGenome decodeBinary(byte[] bytes);

MarkerResult marker(BackboneDefinition backbone, DiploidGenome genome);
MarkerResult marker(
    BackboneDefinition backbone,
    DiploidGenome genome,
    MarkerScheme scheme);

GenomeSequenceCodec sequenceCodec();
GeneSequenceCodec geneSequenceCodec();

GenomeRandom standardRandom(long seed);
```

`synthesize` と `breed` には `long seed` を受け取るconvenience overloadもあります。再現可能な生成・テストではseedを明示します。

## 9. Decode

```java
DecodeResult<MyPhenotype> result = engine.decode(profile, genome);

DecodedGenome physical = result.decodedGenome();
MyPhenotype phenotype = result.phenotype();
DecoderIdentity identity = result.identity();
```

`DecodeResult` は次を含みます。

- `decodedGenome`: Address aggregate、物理gene、必要ならhomology context
- `phenotype`: `GenomeProfile.mapPhenotype(...)` が生成したconsumer型
- `identity`: Engine revision、Engine decoder config fingerprint、Profile descriptor

WGLはここまでを担当し、Phenotype Snapshotの保存やEntityへの適用はconsumerが担当します。

## 10. Founder synthesis

最小例:

```java
SynthesisResult result = wgl.engine().synthesize(
        profile,
        backbone,
        target,
        SynthesisContext.defaults(),
        seed);
```

結果はsealed interfaceです。

```java
if (result instanceof SynthesisResult.Success success) {
    DiploidGenome genome = success.genome();
    DecodeResult<?> decoded = success.decoded();
} else if (result instanceof SynthesisResult.Failure failure) {
    SynthesisFailureReason reason = failure.reason();
    String detail = failure.detail();
}
```

`SynthesisFailureReason`:

| 値 | 意味 |
| --- | --- |
| `INVALID_TARGET` | Target自体が不正 |
| `UNSATISFIABLE_TARGET` | Profile/Backbone上でTargetを満たせない |
| `CONVERGENCE_LIMIT` | 許容試行内で収束しない |
| `SAFETY_REJECTED` | Founder safety policyで拒否 |

Targetをどう抽選するかはconsumer責務です。WGLは渡された `SynthesisTarget` を再現するGenomeを合成します。

## 11. Breeding

最小例:

```java
BreedingContext context = BreedingContext.standard(backbone);

BreedingResult result = wgl.engine().breed(
        profile,
        parentA,
        parentB,
        context,
        seed);
```

結果:

```java
if (result instanceof BreedingResult.Success success) {
    DiploidGenome childGenome = success.genome();
    DecodeResult<?> decoded = success.decoded();
} else if (result instanceof BreedingResult.NoViableOffspring failure) {
    BreedingFailureReason reason = failure.reason();
    String detail = failure.detail();
}
```

正常な「子を作れない」状態は例外ではなく `NoViableOffspring` です。

`BreedingFailureReason`:

| 値 | 意味 |
| --- | --- |
| `CHROMOSOME_COUNT_MISMATCH` | 染色体対数が一致しない |
| `INSUFFICIENT_CROSS_PARENT_HOMOLOGY` | 親間Homologyが不足 |
| `CONSTRAINT_UNSATISFIABLE` | 継承制約を満たせない |
| `INVALID_PARENT_STRUCTURE` | 親Genome構造が繁殖入力として不正 |
| `SAFETY_REJECTED` | Genome safety policyで拒否 |

de novo禁止Address、mutation倍率、親別meiosis policy等が必要な場合は `BreedingContext` へ渡します。

## 12. Binary persistence

WGLは保存先を持ちません。consumerはWGL codecで得たbyte列をPDC、DB、file等へ保存します。

保存:

```java
byte[] genomeBytes = wgl.engine().encode(genome);
```

復元:

```java
DiploidGenome genome = wgl.engine().decodeBinary(genomeBytes);
```

Binary containerにはProfile ID、Backbone ID、Phenotype Snapshot、Entity情報を含みません。それらが必要ならconsumer側の保存形式で別途保持します。

## 13. text representation

Genome bit列のデバッグ・表示変換には `GenomeSequenceCodec` を利用できます。

```java
GenomeSequenceCodec codec = wgl.engine().sequenceCodec();

BitSequence bits = codec.decodeHex(hex);
GenomeSequenceCodec.TextView dna = codec.encodeDna(bits);
```

`TextView` は表示文字列と、表示単位へ変換しきれないresidual bitsを分離して保持します。

## 14. Marker

```java
MarkerResult marker = wgl.engine().marker(backbone, genome);
String text = marker.formatted();
```

Markerは現在の物理Genomeから算出する派生情報です。一意個体ID、lineage ID、親子関係、世代番号の代替にはしません。

## 15. 推奨consumer構成

WGL依存をゲームロジック全体へ散らさず、consumer内で次の境界を作ると更新しやすくなります。

```text
Paper plugin lifecycle
    ↓
WGL service gateway
    ↓
consumer Profile lifecycle
    ↓
consumer genome application service
    ├─ founder synthesis
    ├─ decode
    ├─ breeding
    └─ persistence codec
    ↓
Entity / gameplay / UI
```

特に、Profile登録・差替え・解除を1箇所へ集約し、Entity保存処理では `DiploidGenome` とconsumer Snapshotの責務を混同しない構成を推奨します。

## 16. 公開型の所在

| package / module | consumerから見た用途 |
| --- | --- |
| `co.surumene.wgl.api.*` / `wgl-api` | 公開model、SPI、context、result、codec interface |
| `co.surumene.wgl.plugin.WonderfulGenomeLibService` / `wgl-plugin` | Paper上のservice入口 |
| `co.surumene.wgl.core.*` / `wgl-core` | WGL内部実装。production consumerの通常入口にはしない |

実装詳細を調査する前に、まず本書と公開API型を使用してください。

## 17. 実例

`surumeneco/WhatAWonderfulWolf` の `develop` は現在のconsumer実例です。

特に次のファイルが責務分離の参考になります。

| ファイル | 実例 |
| --- | --- |
| `settings.gradle.kts` | Composite Build |
| `build.gradle.kts` | compile/test依存 |
| `src/main/resources/plugin.yml` | Paper hard dependency |
| `WhatAWonderfulWolfPlugin.java` | service取得 |
| `WglProfileRegistryGateway.java` | registry境界 |
| `WonderfulWolfProfileLifecycle.java` | register / replace / unregister |
| `WonderfulWolfFounderSynthesizer.java` | synthesize利用 |

WWW固有仕様をそのまま別consumerへコピーせず、WGLとの境界の実例として参照します。
