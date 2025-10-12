# Matcha Talk 실행 가이드

## 개요
Matcha Talk은 Spring Boot 기반 백엔드와 Vue 3 + Vite 기반 프론트엔드로 구성된 실시간 커뮤니케이션 서비스입니다. 본 문서는 저장소를 내려받은 뒤 로컬 환경에서 프로젝트를 실행하는 방법을 안내합니다.

## 프로젝트 구조
```
Matcha-Talk/
├─ backend/      # Spring Boot 서버 (Gradle)
├─ frontend/     # Vue 3 + Vite 클라이언트
└─ dev-server.crt
```

## 사전 준비물
- **Git**
- **Java 17** (Gradle Wrapper가 포함되어 있으므로 별도 Gradle 설치는 필요하지 않습니다.)
- **Node.js 18 이상** 및 **npm**
- **MySQL 8.x** (실제 DB 프로필을 사용할 경우)

## 저장소 클론
```bash
git clone <저장소 URL>
cd Matcha-Talk/Matcha-Talk
```

## 백엔드 설정 및 실행
1. **DB 프로필 사용 시 준비**
   - MySQL에 `match_talk_db` 데이터베이스를 생성합니다.
   - backend폴더 내에 First_sql.sql을 이용하여 테이블을 생성합니다.
   - `backend/src/main/resources/application-db.properties` 파일에서 `spring.datasource.username`, `spring.datasource.password` 등을 로컬 환경에 맞게 수정하거나 `.env` 파일로 덮어쓸 수 있습니다.
   - Papago API 키, 메일 계정 등 민감 정보는 `.env` 파일에 `PAPAGO_CLIENT_ID`, `PAPAGO_CLIENT_SECRET` 등의 환경 변수를 정의하여 관리하세요 (`backend` 디렉터리 기준).

3. **서버 실행**
```bash
cd backend
./gradlew bootRun
```
   - Windows 환경에서는 `gradlew.bat bootRun`을 사용하세요.
   - 기본적으로 HTTPS(포트 8080)로 서비스가 기동됩니다. 브라우저에서 경고가 표시될 수 있으므로 필요하다면 로컬 신뢰 저장소에 `dev-server.crt`를 등록하세요.
   - 현재 등록된 HTTPS는 frontend 파일 내의 vite.config.js를 참고하세요.
   - 환경이 다를 경우 로컬 신뢰 저장소에 `dev-server.crt`에 현재 네트워크를 추가 해야 합니다.

## 프론트엔드 설정 및 실행
```bash
cd frontend
npm install
npm run dev
```
- 기본적으로 개발 서버는 `http://localhost:5173`에서 실행되며, 백엔드 API는 `https://localhost:8080`을 참조합니다.
- 다른 포트나 호스트를 사용하려면 `vite.config.js` 혹은 환경 변수를 조정하세요.

## 실행 확인
1. 백엔드(Spring Boot) 서버가 정상적으로 기동되었는지 로그를 확인합니다.
2. 프론트엔드 개발 서버에 접속하여 로그인/채팅 등 주요 기능이 동작하는지 점검합니다.

## 문제 해결
- 포트 충돌이 발생하면 백엔드의 `server.port`, 프론트엔드의 Vite 설정을 변경하세요.


# Matcha Talk 実行ガイド

## 概要
Matcha Talkは、Spring Bootを基盤としたバックエンドと、Vue 3 + Viteを使用したフロントエンドで構成されたリアルタイムコミュニケーションサービスです。  
本書では、リポジトリをクローンしてローカル環境でプロジェクトを実行する手順を説明します。

## プロジェクト構成
```
Matcha-Talk/
├─ backend/      # Spring Boot サーバー (Gradle)
├─ frontend/     # Vue 3 + Vite クライアント
└─ dev-server.crt
```

## 事前準備
- **Git**
- **Java 17**（Gradle Wrapperが含まれているため、別途Gradleのインストールは不要です）
- **Node.js 18以上** および **npm**
- **MySQL 8.x**（実際のDBプロファイルを使用する場合）

## リポジトリのクローン
```bash
git clone <リポジトリURL>
cd Matcha-Talk/Matcha-Talk
```

## バックエンドの設定と実行
1. **DBプロファイルを使用する場合の準備**
   - MySQLで `match_talk_db` データベースを作成します。  
   - `backend` フォルダ内の `First_sql.sql` を使用してテーブルを作成します。  
   - `backend/src/main/resources/application-db.properties` ファイルで、  
     `spring.datasource.username` や `spring.datasource.password` などをローカル環境に合わせて修正するか、`.env` ファイルで上書きすることができます。  
   - Papago APIキーやメールアカウントなどの機密情報は、`.env` ファイルに `PAPAGO_CLIENT_ID`、`PAPAGO_CLIENT_SECRET` などの環境変数を定義して管理します（`backend` ディレクトリ基準）。

3. **サーバーの起動**
```bash
cd backend
./gradlew bootRun
```
   - Windows環境では `gradlew.bat bootRun` を使用してください。  
   - デフォルトでは HTTPS（ポート8080）でサービスが起動します。ブラウザで警告が表示される場合は、ローカルの信頼ストアに `dev-server.crt` を登録してください。  
   - 現在のHTTPS設定は、フロントエンドの `vite.config.js` ファイルを参照してください。  
   - ネットワーク環境が異なる場合は、ローカルの信頼ストア内の `dev-server.crt` に現在のネットワークIPを追加する必要があります。

## フロントエンドの設定と実行
```bash
cd frontend
npm install
npm run dev
```
- デフォルトでは開発サーバーは `http://localhost:5173` で実行され、バックエンドAPIは `https://localhost:8080` を参照します。  
- 他のポートまたはホストを使用する場合は、`vite.config.js` または環境変数を調整してください。

## 動作確認
1. バックエンド（Spring Boot）サーバーが正常に起動しているかログを確認します。  
2. フロントエンドの開発サーバーにアクセスし、ログインやチャットなどの主要機能が動作するかを確認します。

## トラブルシューティング
- ポートの競合が発生した場合は、バックエンドの `server.port` またはフロントエンドの Vite 設定を変更してください。  
- SSL関連の警告が気になる場合は、バックエンドをHTTPで実行するように設定を変更するか、ブラウザに開発用証明書を信頼登録してください。  
- 依存関係のインストールエラーが発生した場合は、Node.js、npm、Javaのバージョンを再確認してください。

## 追加の参考情報
- テストおよびデプロイ環境では、機密情報を `.env` ファイルまたは安全な秘密管理ツールで保管してください。  
- ビルド成果物は `backend/build` および `frontend/dist` に生成されます。

- SSL 관련 경고가 거슬릴 경우 백엔드를 HTTP로 실행하도록 인증서 설정을 수정하거나, 브라우저에 개발용 인증서를 신뢰하도록 등록하세요.
- 의존성 설치 오류가 발생하면 Node.js, npm, Java 버전을 다시 확인하세요.

## 추가 참고
- 테스트 및 배포 환경에서는 민감한 설정을 `.env` 파일이나 안전한 비밀 관리 도구에 보관하세요.
- 빌드 산출물은 `backend/build`, `frontend/dist`에 생성됩니다.
