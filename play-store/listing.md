# PuzzlePop on Google Play: listing and console answers

Everything to paste into the Play Console for version 1.0, plus how each piece of art was made.
The art is in this folder: `icon-512.png`, `feature-graphic.png`, `phone-*.png` and `tablet-*.png`.

## Store settings

| Field | Value |
| --- | --- |
| App name | PuzzlePop |
| Package | io.github.sirwinnybob.puzzlepop |
| App or game | Game |
| Category | Puzzle |
| Price | $0.99 (paid app, no in-app purchases) |
| Contains ads | No |
| Privacy policy | https://github.com/Sirwinnybob/PuzzlePop/blob/main/docs/PRIVACY.md |
| Contact email | _Your choice: Play shows it publicly on the listing_ |
| Website (optional) | https://github.com/Sirwinnybob/PuzzlePop |

## English listing

**Short description** (77 of 80 characters)

> Turn your own photos into jigsaw puzzles. Safe kid mode, no ads, no tracking.

**Full description**

> Turn your favourite photos into jigsaw puzzles. Pick a picture from your gallery or take one with the camera, choose anywhere from 10 to 1000 pieces, and start. Your puzzles save as you go, so you can stop and come back later.
>
> MADE FOR PLAYING
> • Eight piece shapes, from chunky pieces for small hands to classic jigsaw cuts, hearts, ribbons and freeform
> • A tray that sorts loose pieces into edges, insides or one colour
> • The box picture in the corner, and a Peek button to see the whole picture
> • Hints when you're stuck: one piece and where it goes
> • Optional piece rotation for an extra challenge
> • Your best time for every photo and size
> • Confetti, or trumpets and fireworks, when you finish
>
> A KID MODE YOU CAN HAND OVER
> • Big pictures and three puzzle sizes, and nothing else to tap
> • Chunky pieces that pull into place when dropped close
> • The app pins itself to the screen, so little ones can't wander off into other apps
> • Time limits per sitting and per day, with a friendly break screen
> • A helper that shows the next piece if they get stuck
> • Grown-up settings stay behind your fingerprint or a PIN
>
> PRIVATE BY DESIGN
> PuzzlePop has no ads, no accounts, no analytics and no internet access. Your photos never leave your device.
>
> Works on Android 8 and newer, on tablets and phones. In English and Korean.

**Release notes for 1.0**

> First release. Turn your photos into jigsaw puzzles, with a kid mode for little ones.

## Korean listing (optional)

The app is in Korean, so a Korean listing helps Korean-speaking buyers find it. Like the in-app Korean,
this was written with machine help; have a native speaker check it before you publish.

**짧은 설명** (39자)

> 내 사진으로 만드는 직소 퍼즐. 안전한 아이 모드, 광고와 추적 없음.

**자세한 설명**

> 좋아하는 사진으로 직소 퍼즐을 만들어 보세요. 갤러리에서 사진을 고르거나 카메라로 찍은 뒤, 10조각부터 1000조각까지 원하는 크기를 골라 바로 시작해요. 퍼즐은 자동으로 저장되니 언제든 멈췄다가 이어서 할 수 있어요.
>
> 즐겁게 맞추기
> • 작은 손에 맞는 큼직한 조각부터 클래식 직소, 하트, 리본, 자유형까지 여덟 가지 조각 모양
> • 남은 조각을 가장자리, 안쪽, 색깔별로 나눠 보여 주는 조각 상자
> • 구석에 보이는 상자 그림과 전체 그림을 잠깐 보는 엿보기 버튼
> • 막혔을 때 조각 하나와 들어갈 자리를 알려 주는 힌트
> • 더 어렵게 즐기고 싶다면 조각 회전
> • 사진과 크기마다 최고 기록 저장
> • 다 맞추면 색종이, 또는 트럼펫과 불꽃놀이
>
> 마음 놓고 건네줄 수 있는 아이 모드
> • 큰 사진과 세 가지 퍼즐 크기만 보여요
> • 가까이 놓으면 제자리로 쏙 들어가는 큼직한 조각
> • 화면 고정으로 아이가 다른 앱으로 나가지 못해요
> • 한 번에, 하루에 놀 수 있는 시간 제한과 쉬는 시간 화면
> • 막히면 다음 조각을 알려 주는 도우미
> • 어른 설정은 지문이나 PIN으로 잠겨 있어요
>
> 개인정보 보호
> PuzzlePop에는 광고, 계정, 분석 기능이 없고 인터넷도 사용하지 않아요. 사진은 기기 밖으로 나가지 않아요.
>
> Android 8 이상 태블릿과 휴대폰에서 사용할 수 있어요. 영어와 한국어를 지원해요.

## App content answers

| Console section | Answer |
| --- | --- |
| Privacy policy | The URL above |
| Ads | No ads |
| App access | All functionality is available without special access. (The grown-up PIN is created by the user on first launch.) |
| Content rating | Questionnaire: puzzle game, no violence, no user interaction or sharing, no purchases of digital goods, no location sharing. Expect Everyone / PEGI 3 / IARC 3+. |
| Target audience | Include ages under 5, 6–8 and 9–12 (it's built for young children) plus older ages. This puts the app under the Families policy. |
| Families: ads and SDKs | No ads, no third-party SDKs, no data collection |
| Data safety | Data collected: none. Data shared: none. (Photos and settings stay on the device and are never transmitted; the app has no internet permission.) |
| News app / Government / Financial / Health | No |

## Before publishing

- New personal developer accounts must run a closed test with at least 12 testers for 14 days in a row before production. Upload `app-release.aab` to the closed testing track first.
- Accept Play App Signing. Your existing release key (`~/.puzzlepop/puzzlepop-release.jks`) becomes the upload key; keep it backed up.
- Keep `app/build/outputs/mapping/release/mapping.txt` from each upload (Play asks for it for readable crash reports).
- A paid app can later be made free, but never the other way round.

## Remaking the art

1. Install the debug build named plain "PuzzlePop" (temporarily set `app_name` in `app/src/debug/res/values/strings.xml`, build, then restore it).
2. Load sample photos (the README credits the CC0 / public-domain ones used here) and set up the screens.
   In debug builds, long-pressing the piece counter solves the top half of a puzzle, and a second long-press
   solves all but one piece.
3. Capture with `adb exec-out screencap -p > raw/<name>.png`, using the names in `tools/store_art.py`.
   Check each one for notifications before using it.
4. Render the icon: `magick -background none -density 900 <(sed 's/viewBox="0 0 108 108"/viewBox="17 17 74 74"/' tools/launcher-icon/preview.svg) -resize 512x512 -depth 8 icon-512.png`
5. Run `python3 -I tools/store_art.py raw play-store` to frame the screenshots and build the feature graphic.
   For tablet shots it also cuts off Samsung's taskbar and inpaints the floating S Pen button and edge-panel
   handle (needs `python3-opencv`).

Upload `phone-*.png` as phone screenshots and `tablet-*.png` as both 7-inch and 10-inch tablet screenshots.
