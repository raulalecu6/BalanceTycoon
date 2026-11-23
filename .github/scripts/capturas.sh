#!/bin/bash
# Instala el APK en el emulador y saca capturas de las pantallas principales.
# Lo ejecuta GitHub Actions dentro de reactivecircus/android-emulator-runner.
set -x
PKG=com.alecu.balancetycoon
ACT=$PKG/.MainActivity
OUT=capturas
mkdir -p $OUT
adb install -r apk/app-debug.apk
adb shell cmd locale set-app-locales $PKG --locales es-ES || true

shot() { sleep "$2"; adb exec-out screencap -p > "$OUT/$1.png"; }

# 1) Partida nueva, recién abierta.
adb shell am start -W -n $ACT --ef night 0
shot 01-partida-nueva 10

# 2) Partida de demostración: 150 minutos de juego automático (local de Ciudad Real).
adb shell am force-stop $PKG
adb shell am start -W -n $ACT --ef night 0 --ei demo_minutes 150
shot 02-oficina-150min 45
for t in 1 2 3 4; do
  adb shell am start -n $ACT --activity-single-top --ei tab $t
  shot "0$((t + 2))-pestana-$t" 5
  if [ $t -eq 3 ]; then
    # Clientes: baja hasta las consultas pendientes (opción múltiple y asiento).
    adb shell input swipe 540 1500 540 500 400
    shot 05b-clientes-consultas 3
    adb shell input swipe 540 1500 540 700 400
    shot 05c-clientes-consultas-2 3
  fi
done
adb shell am start -n $ACT --activity-single-top --ei tab 0
adb shell input swipe 540 900 540 1500 300
shot 07-oficina-desplazada 3

# 3) Partida muy avanzada: 600 minutos (torre de Madrid).
adb shell am force-stop $PKG
adb shell am start -W -n $ACT --ef night 0 --ei demo_minutes 600
shot 08-torre-600min 90
adb shell input swipe 540 600 540 1600 300
shot 09-torre-arriba 3

# 4) La misma torre de noche: cielo estrellado, farolas y ventanas encendidas.
adb shell am start -n $ACT --activity-single-top --ef night 1.0 --ei tab 0
shot 10-torre-noche-arriba 4
# Arrastra dentro de la torre (no en la lista de abajo) para bajar hasta la recepción.
adb shell input swipe 540 1150 540 350 300
adb shell input swipe 540 1150 540 350 300
shot 11-torre-noche-recepcion 3

adb logcat -d '*:E' | grep -i -A 20 "balancetycoon\|AndroidRuntime" | head -200 > $OUT/errores.txt || true
ls -l $OUT
