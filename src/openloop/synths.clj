(ns openloop.core
  (require [overtone.core]
           ;; [openloop.constants]
           ))

(defsynth input
  "routs the input to the output and the recorders"
  [amp 1 rec-bus 50 dir-bus 60]
  (let [
        my-in (sound-in (range nr-chan))
        dir-sig (* my-in amp)]
    (out rec-bus my-in)
    (out dir-bus dir-sig)))

(defsynth master-rec
  "records the master loop"
  [start [0 :tr] stop [0 :tr] timer-bus 40 in-bus 50 loop 1 which-buf 0]
  (let [
        is-recording (set-reset-ff:kr start stop)
        my-in (in:ar in-bus nr-chan)
        my-timer (timer:kr (+ start stop))]
    (out:kr timer-bus my-timer)
    (record-buf:ar my-in which-buf 0 1 0 is-recording loop)))

(defsynth slave-rec
  "records the slave loops"
  [start [0 :tr] stop [0 :tr] in-bus 50 loop 1 which-buf 0]
  (let [
        is-recording (set-reset-ff:kr start stop)
        my-in (in:ar in-bus nr-chan)]
    (record-buf:ar my-in which-buf 0 1 0 is-recording loop)))

(defsynth master-play
  "plays back the master loop"
  [rate 1 nr-bars 1 which-buf 0 amp 1 atk-thres 0.002 timer-bus 40 length-bus 80 downbeat-bus 90 nr-bars-bus 100 out-bus 70 ]
  (let [
        my-timer (mod (in:kr timer-bus) max-loop-seconds )
        start (max 0 (/ (index-in-between:kr which-buf atk-thres) nr-chan))
        end (* my-timer SR)
        length (- end start)
        downbeat (impulse:kr (/ (* nr-bars SR) length))
        buf-start (pulse-divider:kr downbeat nr-bars (- nr-bars 1))
        phs (+ start (sweep:ar buf-start SR))
        sig (* (buf-rd:ar nr-chan which-buf phs 1 1) amp)]
    (send-trig:kr downbeat)
    (out:kr length-bus length)
    (out:kr downbeat-bus downbeat)
    (out:kr nr-bars-bus nr-bars)
    ;; (def test (+ (* 0.5 (decay downbeat 0.1) (sin-osc 880)) sig))
    (out:ar out-bus sig)))

(defsynth slave-play
  "play back a slave loop"
  [rate 1 jump [0 :tr] length-mul 1 which-buf 0 del 0 atk-thres 0.002 amp 1 nr-bars-bus 100 downbeat-bus 90 length-bus 80 out-bus 70]
  (let [
        length (* (in:kr length-bus) length-mul)
        nr-bars (in:kr nr-bars-bus)
        downbeat (in:kr downbeat-bus)
        start (max 0 (floor (/ (index-in-between:kr which-buf atk-thres) nr-chan)))
        end (+ start length)
        my-sync (set-reset-ff:kr downbeat 0)
        jump-trig (env-gen:kr (env-adsr 0.001 0.001 0 1 1 0) my-sync)
        phs (select:ar my-sync [(dc:ar 0) (phasor:ar (+ jump-trig jump) (buf-rate-scale:kr which-buf) start end start)])
        sig (buf-rd:ar nr-chan which-buf phs 1 1)]
    (out:ar out-bus sig)))

;; (def test (+ (* 0.5 (decay downbeat 0.1) (sin-osc 880)) sig))

(defsynth output
  "mix everything and send it out"
  [rec-bus 70 dir-bus 60 dir-amp 1 main-amp 1 out-bus 0]
  (let [
        rec-sig (in:ar rec-bus nr-chan)
        dir-sig (in:ar dir-bus nr-chan)
        out-sig (* main-amp (+ dir-sig rec-sig))]
    (out:ar out-bus out-sig)))
