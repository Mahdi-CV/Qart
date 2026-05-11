# Qart Web

A Spring Boot web application that exposes the [CuteR](https://github.com/chinuno-usami/CuteR) artistic QR code engine as a self-hosted web service. Upload an image, enter a URL or text, and get back a styled QR code that embeds the image.

> The original Android app lives on the [`master`](https://github.com/Mahdi-CV/Qart/tree/master) branch. This `web` branch is a standalone deployment of the same engine over HTTP.

---

## What's new in the web version

- **REST API** — `POST /api/qr/generate` accepts multipart form data and returns a PNG
- **Web UI** — browser-based interface, no app install needed
- **QR verification** — every generated code is decoded and verified to match the original input
- **Configurable output** — control dot color, colorful mode, embedded image position, background image, and output resolution (1–4×)
- **Self-hostable** — runs as a standard Spring Boot jar, deployable anywhere Java 17+ is available

---

## Stack

| Layer     | Technology          |
|-----------|---------------------|
| Runtime   | Java 17             |
| Framework | Spring Boot 3.2.5   |
| QR engine | CuteR (ZXing-based) |
| Build     | Maven               |

---

## API

### `POST /api/qr/generate`

Generates an artistic QR code as a PNG.

**Parameters** (multipart/form-data):

| Parameter     | Type    | Required | Default   | Description                                  |
|---------------|---------|----------|-----------|----------------------------------------------|
| `text`        | string  | yes      | —         | The URL or text to encode                    |
| `mode`        | string  | no       | `NORMAL`  | QR style: `NORMAL`, `BLACKWHITE`, `COLORFUL` |
| `colorful`    | boolean | no       | `false`   | Enable colorful dot mode                     |
| `color`       | string  | no       | `#000000` | Dot color (hex)                              |
| `image`       | file    | no       | —         | Foreground image to embed in the QR          |
| `bgImage`     | file    | no       | —         | Background image                             |
| `embedX`      | int     | no       | `0`       | X offset of embedded image                  |
| `embedY`      | int     | no       | `0`       | Y offset of embedded image                  |
| `bgScale`     | float   | no       | `1.0`     | Background image scale                       |
| `bgOffsetX`   | int     | no       | `0`       | Background X offset                          |
| `bgOffsetY`   | int     | no       | `0`       | Background Y offset                          |
| `outputScale` | int     | no       | `1`       | Output resolution multiplier (1–4)           |

**Response**: `image/png` with headers:
- `X-QR-Verified: true/false` — whether the generated QR decodes back to the original text
- `X-QR-Decoded` — the text decoded from the generated QR

---

## Running locally

### Prerequisites

- Java 17+
- Maven 3.8+

### Run

```bash
git clone https://github.com/Mahdi-CV/Qart.git -b web
cd Qart
mvn spring-boot:run
```

The app starts on `http://localhost:8080` by default.

To change the port:

```bash
mvn spring-boot:run -Dspring-boot.run.arguments=--server.port=9000
```

### Build a jar

```bash
mvn package -DskipTests
java -jar target/qart-web-1.0.0.jar
```

---

## Deployment

### First-time setup

```bash
# 1. Clone the web branch to your deploy directory
git clone https://github.com/Mahdi-CV/Qart.git -b web /your/deploy/path
cd /your/deploy/path

# 2. Build
mvn package -DskipTests

# 3. Run
java -jar target/qart-web-1.0.0.jar --server.port=8080
```

To run as a background service, install the included systemd unit:

```bash
# Edit qart-web.service to set your deploy path and port, then:
cp qart-web.service /etc/systemd/system/
systemctl daemon-reload
systemctl enable qart-web
systemctl start qart-web
```

### Deploying updates

```bash
# 1. Make changes locally and push
git checkout web
git add -A
git commit -m "describe your change"
git push origin web

# 2. On the server, pull and rebuild
./deploy.sh
```

`deploy.sh` does the following:

```bash
git pull origin web          # pull latest from GitHub
mvn package -DskipTests -q  # rebuild the jar
systemctl restart qart-web   # restart the service
```

### Checking logs

```bash
systemctl status qart-web
journalctl -u qart-web -f
```

---

## Repository structure

```
web branch
├── src/
│   └── main/
│       ├── java/io/github/scola/qart/web/
│       │   ├── Application.java               # Spring Boot entry point
│       │   ├── controller/QrController.java   # REST endpoint
│       │   ├── engine/CuteR.java              # QR generation engine
│       │   └── service/QrService.java         # Business logic
│       └── resources/
│           ├── application.properties
│           └── static/index.html              # Web UI
├── pom.xml
└── deploy.sh                                  # Pull, build, and restart
```

---

## License

[GPLv3](https://www.gnu.org/licenses/gpl-3.0.en.html)
