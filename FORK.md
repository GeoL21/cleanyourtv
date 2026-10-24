# Fork de GeoL21: launcher con fondos de video

Este fork agrega, sobre el launcher de Clean Your TV:

| Qué | Cómo |
|---|---|
| **Fondos de video** | Videos propios (.mp4, .mkv, .webm) en silencio y en bucle detrás del Inicio. Se pausan y sueltan el decodificador al abrir una app. Se envían con `tools/videos.sh <carpeta>`. Rotan según `wallpaper_rotation_minutes`. |
| **Saludo fijo** | Clave de tema `greeting_text` (p. ej. `"Hola"`), en minúsculas normales y más grande, seguida de `greeting_name`. |
| `video_wallpaper` | `false` desactiva los videos sin borrarlos. |
| `video_dim` | 0 a 90: sombra negra sobre el video para que el reloj se lea mejor. |

Pendiente en próximas versiones: fila "Seguir viendo" y vitrina, centro de control, clima y menú HDMI de vidrio.

Los videos y el tema personal (nombre, ciudad) **no** se suben a este repositorio: viven en el PC.
