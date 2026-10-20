# 腕立てFlappy

腕立て伏せ1回で鳥が羽ばたく、Flappy Bird風のAndroidゲームです。スマホのインカメラと [MediaPipe Pose Landmarker](https://ai.google.dev/edge/mediapipe/solutions/vision/pose_landmarker) で体の動きを検知します。今後、姿勢検知を使ったミニゲームを追加していける構成になっています。

## インストール

1. GitHubの [Actions](../../actions/workflows/build.yml) で最新の成功したビルドを開きます。
2. Artifacts の `pushup-flappy-apk` をダウンロードして展開し、`app-debug.apk` をスマホに入れます。
3. スマホで「提供元不明のアプリ」のインストールを許可して、APKを開きます。

`v0.1.0` のようなタグをpushすると、APKを添付したReleaseが作られます。スマホから直接ダウンロードしたいときはこちらが便利です。

固定のデバッグ署名鍵（`app/keystore/debug.keystore`）で署名しているので、新しいビルドは前のバージョンに上書きインストールできます。

## 遊び方

1. スマホを床に立てかけ、インカメラに上半身（肩）が映るようにします。顔の前に置いても、体の横に置いてもかまいません。
2. 腕立て伏せを1回するとゲームが始まります。
3. 体を下ろして上げるたびに鳥が羽ばたきます。土管にぶつからないように進みます。

### 操作方式

メニューで選べます。

- **羽ばたき**: 腕立て1回で1回羽ばたきます。上昇したあと、重力でまた下降します。
- **連動**: 体の高さがそのまま飛行機の高さになります。体を上げると飛行機が上がり、下ろすと下がります。岩のすき間に合わせて、その高さで体をキープするのがポイントです。最初の腕立て1回で、上下の動きの幅を覚えます。

- 右上には、カメラ映像と検出した骨格が表示されます。
- 左のゲージは体の高さを表します。オレンジは体を下ろした状態、緑は上げた状態です。
- 体が映らなくなると一時停止します。
- 「背景をカメラ映像にする」をオンにすると、ゲームの背景が自分のカメラ映像になり、腕立てのフォームを見ながら遊べます。人物はくっきりのまま、周りの部屋はぼかします（MediaPipeの人物セグメンテーションを使用）。明るい部屋でゲームが見えにくいときは「カメラ映像を少し暗くする」も使えます。
- 「顔をアバターで隠す」をオンにすると、カメラ映像の顔をひよこのアバターで隠します。右上の小さい映像にも適用されます。
- ベストスコアは、見た目・操作方式・難易度の組み合わせごとに記録します。タップ操作モードのスコアも別扱いです。
- メニューの「タップ操作モード」を使うと、カメラなしで画面タップで遊べます。動作確認用です。

## 腕立てシューター

80年代のアーケード風の横スクロールシューティングです。

- 体の高さで自機を上下に動かします（連動）。ショットは自動で連射します。
- 編隊を全滅させるか、紫の砲台を倒すとカプセルが出ます。カプセルを取るとパワーアップゲージのカーソルが進みます。
- 欲しい項目が光っているときに**腕立て伏せを1回すると、そのパワーアップを獲得**します。
  - SPEED: 体の動きへの追従が速くなる（4段階）
  - MISSILE: 斜め下に飛ぶミサイル
  - DOUBLE: 斜め上にも撃つ
  - OPTION: 自機の動きを追いかける分身（2機まで）
- 残機は3。やられるとパワーアップはすべて失います。
- ドット絵は `tools/shooter_art.py` で生成したオリジナルです。



```
app/src/main/java/com/c016/pushupflappy/
├── MainActivity.kt          ゲーム選択メニュー
├── GameActivity.kt          カメラ・姿勢推定・ゲームを接続
├── camera/PoseCamera.kt     CameraX → 正立・左右反転した画像
├── pose/
│   ├── Pose.kt              ランドマーク（MediaPipeの33点形式）
│   ├── PoseEstimator.kt     姿勢推定エンジンのインターフェース
│   └── MediaPipePoseEstimator.kt
├── motion/PushUpDetector.kt 肩の高さから腕立て回数を数える
└── game/
    ├── MiniGame.kt          ミニゲームのインターフェース
    ├── GameCatalog.kt       メニューに出すゲームの一覧
    ├── GameView.kt          ゲームループ（SurfaceView）
    ├── PoseOverlay.kt       カメラ映像と骨格の表示
    ├── PoseHud.kt           ゲーム共通のHUD（文字、体の高さゲージなど）
    ├── flappy/              FlappyGame（ゲーム本体）とテーマ（*Skin）
    └── shooter/             ShooterGame とパワーアップゲージ
```

- **ゲームを追加する**: `MiniGame` を実装し、`GameCatalog.games` に登録します。動作判定は `motion/` に検出器として追加します。
- **姿勢推定エンジンを変える**: `PoseEstimator` を実装します（例: RTMPose + ONNX Runtime）。出力はMediaPipeの33点の番号に合わせます。

## 見た目（テーマ）

メニューで切り替えられます。

- **クラシック**（デフォルト）: Flappy Bird風のドット絵。鳥、土管、背景、数字などはこのリポジトリ用に新しく描いたもので、本家の画像ファイルは使っていません。`tools/classic_art.py` で生成しています（`python3 tools/classic_art.py`、Pillowが必要）。
- **Tappy Plane**: [Kenney](https://kenney.nl/) の「[Tappy Plane](https://kenney.nl/assets/tappy-plane)」パックの素材（CC0）です。ライセンスは `licenses/kenney-tappy-plane-CC0.txt` を参照してください。

テーマごとの描画と障害物の形（当たり判定）は `game/flappy/FlappySkin.kt` を継承したクラス（`ClassicSkin`、`TappySkin`）にまとまっています。

## ビルド

```
./gradlew assembleDebug
```

JDK 17とAndroid SDK（compileSdk 35）が必要です。姿勢推定モデル（`pose_landmarker_lite.task`）は、初回ビルド時に自動でダウンロードされます。
