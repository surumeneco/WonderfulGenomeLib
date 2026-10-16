# WWC 2.0.0 / WWW 1.0.0 / WGL 1.0.0: メインスレッド停滞・Genome保存性能の最適化方針

- 記録日: 2026-10-16
- 状態: **調査で確認された経路に対する対策案。コード未変更・実機再測定未実施**
- 範囲: WhatAWonderfulChicken / WhatAWonderfulWolf / WonderfulGenomeLib
- 既存の横断設計正本: Google Drive `forGPT/XPlayServer/OriginalPlugins/共通基盤/Genome非同期移行・実行負荷.md`
- WGL性能検証: Google Drive `forGPT/XPlayServer/OriginalPlugins/WGL/開発・検証/06_性能最適化計画.md` と `07_性能・遺伝統計検証と対策.md`

## 0. 未完了作業のみ（2026-10-16、PR・配布設定の現況照合）

以下は**本方針で未完了**の事項。各項目は設計・実装・回帰検証・実VPS評価を一括完了とみなさず、段階を分ける。

| 優先 | 所有者 | 残作業 | 完了判定 |
| --- | --- | --- | --- |
| P0-B | WGL | `MarkerEngine.findHits` の48bit窓比較・逆方向探索の割当/計算削減、密集anchorの厳密探索コスト測定 | 現行Marker Scheme V1との全量Golden一致、独立境界テスト、実処理CPU・割当比較 |
| P0-A | WWC | cargo・装備・runtimeの保存から不変GenomeのMarker再計算を除外。Genome変更/欠損時だけ正規再導出 | `WonderfulChickenStore.save`・ClickMobs/旧個体復元・reloadの回帰、保存ごとのMarker呼出回数削減 |
| P1 | WWC / WWW | runtime状態のみ更新する際のGenome/Snapshot再encode・外部ファイル照合を不要にし、既存参照を保持 | 更新/再起動/失敗時の旧参照維持・copy-on-write・チェックサム・原子的commitの回帰 |
| P2 | WWC / WWW / サーバー | 通常時約1秒のラグと残存する定期走査、AI/特性、CoreProtect等の負荷をspark・TPS/MSPT・GC・I/Oで切り分け | 平常時・高負荷時の計測記録、原因別の採否、Watchdog再発検証 |

**未完了のまま維持する共通検証**: WGL/consumerのGolden完全一致、WWC GUI/cargo/Founder/繁殖/管理/legacy/ClickMobs、WWW runtime/繁殖/復元、Paper実機でのwatchdog・p95/p99・TPS/MSPT・GC評価。詳細は第3節。実装済みと実機検証済みを混同しない。

**関連する別計画（このラグ対策に混入しない）**: WWC Config外部化・旧互換設定整理（[Google Drive](https://drive.google.com/file/d/1neNKLqhF-TUBQ3l2X3TyfvrNBxefsjq9/view)）、WWWの10能力`stats.*`公開（[Google Drive](https://drive.google.com/file/d/1TcqJUvsTK-h0tLGjG8SePC9KJ3s7Hs9x/view)）は両者とも実装前分析。WGL PR #25後の再プロファイリング（[Google Drive](https://drive.google.com/file/d/1Dab0dPvC5ajAvZdMBsEtU4reW5F95eL_/view)）も別途未完了。

## 1. 観測と根拠

2026-10-16のPaperログで、メインスレッドが長時間停止するWatchdog報告を確認。ダンプの実行経路は概ね以下。

```text
WWC InventoryService.syncCargo
  -> WonderfulChickenStore.save
  -> WonderfulChickenStore.genomeMarker
  -> WGL WonderfulGenomeEngine.marker
  -> MarkerEngine.marker / fingerprint / resolveLocus / findHits
```

- 当該事象は通信遅延だけでは説明できず、メインスレッド停止により移動・コマンド・ブロック反映などが滞る。
- 一方、長時間停止以外の平常時に見られる約1秒のラグを**すべて同原因と断定しない**。TPS/MSPT・spark・GC・I/O実測で分離する。
- 本記録は確認済みのコード上の負荷要因と対策の検討事項を区別する。修正・性能改善の成功を意味しない。

### 根拠コード

- WGL: `wgl-core/.../MarkerEngine.java` の `findHits` は各48bit窓で `slice` と `reverse` とHamming距離を反復し、`resolveLocus` は2つの候補集合を組み合わせる。
- WWC: `service/WonderfulChickenStore.java` の `save` が `data.hasGenomeModel()` なら無条件に `genomeMarker(data)` を再実行する。
- WWC: `gui/InventoryService.java` の `syncCargo` はメインスレッドで `store.save` に至る。
- WWC/WWW: `persistence/GenomePayloadFiles.java` は `writeOrReuse` で同内容のGenome/Phenotypeを比較するが、consumerの保存呼出元では事前にGenomeバイナリエンコード等を行う。
- WWC: `task/IntegrityController.java` は処理数の分散上限を設けているが、定期スキャンと投影はプロファイル上の確認対象。
- WWW: `runtime/WonderfulWolfAbilityRuntime.java` も上限付き更新を採用しており、今回のダンプによって停止原因として確定したものではない。

## 2. 最適化案と優先順位

### P0-A: WWC保存時に不変GenomeのMarkerを再計算しない

- `WonderfulChickenStore.save` の呼出を「Genome/Backboneが更新される経路」と「runtime状態・装備・インベントリ等だけを更新する経路」に分離する。
- Genomeが変更されない保存では、正しく確定済みのMarkerを再利用する（保存値の欠損・スキーマ移行時は検証して復旧）。単なる`bloodlineId`の有無のみを信用せず、対応するGenomeと設定を検証可能な無効化条件を用意する。
- 新規Founder、通常繁殖、管理コマンド指定Genome、Genome移行・復元、変更を伴うデータ修復ではMarkerを必ず正規導出する。派生値と物理Genomeの整合性を犠牲にしない。
- 高負荷計算はBukkit/PDC操作と切り離せる純粋入力で扱う。非同期化が必要なら既存の有界ワーカー・復旧契約に合わせ、メインスレッドで完了待ちを行わない。

### P0-B: WGL MarkerEngineの48bit窓探索を割当削減・計算高速化

- `findHits` 内で各窓ごとに`BitSequence.slice/reverse`を生成しない。packed bit列への読み取り専用アクセスまたは64bit rolling window、`Long.bitCount`等による48bit比較へ置換する方向を実測検証する。
- 前方向／逆方向、Hamming距離0～2、候補の位置・距離・orientation、2アンカー間の順序、Hamming→間隔誤差→物理開始座標のtie-break、失敗時fallback、SHA-256入力をすべて現行仕様と一致させる。
- 重複anchorが多数現れる特殊Genomeでは候補数の二重積が増える。候補密度と比較回数を計測した上で、結果を変えない索引化・厳密枝刈り等を個別検討する。
- 全体共有の無制限cacheは避ける。WGLはPaperに依存しない純粋同期演算ライブラリを維持する。

### P1: WWC/WWWのGenomeとruntime保存処理を分離

- インベントリ、行動モード、スタミナ、好感度などのruntime状態しか変化していない場合は、既存の不変Genome/Phenotypeファイル参照を保持し、Genome binaryの再encode、全量比較、ファイル処理を行わない。
- Genome/Phenotype変更時は従来のcopy-on-write、checksum、原子的公開、旧参照維持、PDC commit順を保持する。
- コンシューマ側でEntity/PDCはメインスレッドに限定し、CPU重処理とブロッキングI/Oの区別を計測する。遅延書込みや非同期化を導入する場合は、データ消失、整合性、終了時処理、ClickMobs等の復元を設計に含める。

### P2: 平常時の周期処理・他プラグイン負荷を計測

- WWCの登録個体走査・チャンク走査、血統祖先補完の全Chicken探索、WWW能力・AI/特性処理、CoreProtect等のブロック更新負荷を候補として計測。
- 既に採用済みの分散上限を撤廃せず、発生頻度、1tickあたりの負荷、ホットスポットの有無を確認してから変更する。今回のWatchdogだけで別の処理を犯人扱いしない。

## 3. 検証と完了条件

1. **意味論の完全一致**: Genome Format V1、Marker Scheme V1、既存物理bit列・Marker・親子血統Snapshot・fallback・orientation・tie-breakが変更前後で一致すること。固定seed Golden・通常/欠損/変異/重複anchor/長大配列・A/B順序入替を含む。
2. **WWC機能回帰**: GUI開閉、cargo出し入れ、装備、スタミナ、自然/トラップFounder、繁殖、管理コマンド、legacy移行、ClickMobs復元、reload/再起動時のMarker・参照整合性を確認する。
3. **WWW保存回帰**: runtime状態変更、繁殖・生成、復元・再起動、外部参照ファイルの異常・部分失敗時の整合性を確認する。
4. **性能計測**: 同一Genome群に対する`marker`のCPU・wall・割当bytes・p95/p99、保存ごとのWGL呼出回数とI/O、PaperのTPS/MSPT・GCを比較する。CIランナーの値を本番VPSのTPSに換算しない。
5. **実機判定**: 長いWatchdog停止の再発がないことを確認し、通常時の約1秒ラグはspark等で別途診断する。未測定の改善率を記載しない。

## 4. 実装順序と運用境界

- 最初にWGL MarkerEngineの単体Goldenとコスト測定を用意し、P0-Bを意味論を維持して最適化する。
- WWC側のP0-Aを、正しいMarkerキャッシュ無効化および保存経路分離とともに実装する。単純な「値が非nullなら再計算しない」は不採用。
- WWC/WWWのP1は保存スキーマの互換・復元契約を壊さない独立変更として扱う。
- 変更は各リポジトリの`develop`起点`feature/*`でTDD/CIを通し、PR経由で統合する。正式リリースまでは`main`を更新しない。作業前にGoogle Driveの対応設計正本との整合を確認する。
- 本文は設計・調査記録であり、コード修正・本番デプロイは未実施。

## 5. 完了済みの関連変更（履歴・再実装不要）

完了は**対象PRの統合・リリースというコード上の状態**を表す。今回のMarker/保存経路改善や実サーバーでのラグ解消を完了と認定したものではない。詳細な履歴や測定値は各PRと上記WGL 06/07に集約し、未完了表に再掲載しない。

| 対象 | 既存の完了範囲 | 証跡 |
| --- | --- | --- |
| WGL | Homology系の既採用最適化（DP上界枝刈りまで）と1.0.0リリース | [PR #25](https://github.com/surumeneco/WonderfulGenomeLib/pull/25)、[PR #27](https://github.com/surumeneco/WonderfulGenomeLib/pull/27) |
| WWC | 旧個体Genome非同期移行、Founder/繁殖の有界非同期化と外部不変Genome保存 | [PR #40](https://github.com/surumeneco/WhatAWonderfulChicken/pull/40)、[PR #41](https://github.com/surumeneco/WhatAWonderfulChicken/pull/41) |
| WWC | WGL Markerによる血統表示、定期処理の増分・分散化、血統GUI統一、騎乗Skeleton AIへの操縦委譲、2.0.0リリース | [PR #43](https://github.com/surumeneco/WhatAWonderfulChicken/pull/43)、[PR #46](https://github.com/surumeneco/WhatAWonderfulChicken/pull/46)、[PR #48](https://github.com/surumeneco/WhatAWonderfulChicken/pull/48)、[PR #50](https://github.com/surumeneco/WhatAWonderfulChicken/pull/50)、[PR #52](https://github.com/surumeneco/WhatAWonderfulChicken/pull/52) |
| WWW | Founder/繁殖の非同期処理と外部Genome保存、復元・初期状態修正、分散更新、唸り・攻撃速度・血統表示の修正、騎乗Evoker AIへの操縦委譲、1.0.0リリース | [PR #22](https://github.com/surumeneco/WhatAWonderfulWolf/pull/22)、[PR #23](https://github.com/surumeneco/WhatAWonderfulWolf/pull/23)、[PR #24](https://github.com/surumeneco/WhatAWonderfulWolf/pull/24)、[PR #27](https://github.com/surumeneco/WhatAWonderfulWolf/pull/27)、[PR #30](https://github.com/surumeneco/WhatAWonderfulWolf/pull/30)、[PR #31](https://github.com/surumeneco/WhatAWonderfulWolf/pull/31)、[PR #33](https://github.com/surumeneco/WhatAWonderfulWolf/pull/33)、[PR #35](https://github.com/surumeneco/WhatAWonderfulWolf/pull/35) |

**区別**: 既存の「Genomeを外部ファイルに保存する仕組み」は統合済みだが、P1の「runtimeしか変わらない保存ではGenomeを再encodeしない」は未完了。既存の定期更新分散化も統合済みだが、P2の実測と残存ホットスポットの識別は未完了。
