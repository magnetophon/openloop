(ns openloop.core
  ;; (require [overtone.core]
           ;; [openloop.constants]
           ;; )
  )

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
        ;; nr-bars (in:kr nr-bars-bus)
        downbeat (in:kr downbeat-bus)
        start (max 0 (floor (/ (index-in-between:kr which-buf atk-thres) nr-chan)))
        end (+ start length)
        my-sync (set-reset-ff:kr downbeat 0)
        ;; jump-trig (env-gen:kr (env-adsr 0.001 0.001 0 1 1 0) my-sync)
        ;; jump-trig (env-gen:kr (env-adsr 0 0.001 0 0 1 0) my-sync)
        jump-trig downbeat
        phs (select:ar my-sync [(dc:ar 0) (phasor:ar (+ jump-trig jump) (buf-rate-scale:kr which-buf) start end start)])
        sig (buf-rd:ar nr-chan which-buf phs 1 1)]
    (out:ar out-bus sig)))

;; (show-graphviz-synth slave-play)


;; (def test (+ (* 0.5 (decay downbeat 0.1) (sin-osc 880)) sig))

(defsynth output
  "mix everything and send it out"
  [rec-bus 70 dir-bus 60 dir-amp 1 main-amp 1 out-bus 0]
  (let [
        rec-sig (in:ar rec-bus nr-chan)
        dir-sig (in:ar dir-bus nr-chan)
        out-sig (* main-amp (+ dir-sig rec-sig))]
    (out:ar out-bus out-sig)))


(defonce __DISKRECORDER__
  (defsynth disk-recorder
    [out-buf 0]
    (disk-out out-buf (in 50 nr-chan))))

(defn disk-recording-start
  "Start recording a wav file to a new file at wav-path. Be careful -
  may generate very large files. See buffer-stream for a list of output
  options.
  Note, due to the size of the buffer used for transferring the audio
  from the audio server to the file, there will be 1.5s of silence at
  the start of the recording"
  [group path & args]
  (if-let [info (:recorder (:value @fsm-state ))]
    ((throw (Exception. (str "Recording already taking place to: "
                             (get-in info [:buf-stream :path]))))))

  (let [
        ;; path (resolve-tilde-path path)
        bs   (apply buffer-stream path args)
        rec  (disk-recorder [:head group] bs)]
    ;; rec  (disk-recorder  bs)]
    (swap! fsm-state assoc-in [:value :recorder] {:rec-id rec
                                                  :buf-stream bs})
    :recording-started))


;; (show-graphviz-synth disk-recorder)
;; (show-graphviz-synth disk-rec+count)

(defsynth disk-rec+count
  "record a file and start counting frames, outputting them on trigger"
  ;; [group rec-clock-bus now-bus trig ]
  ;; [group 0, path "/tmp/openloop.wav", rec-clock-bus 1000, now-bus 1001, trig [0 :tr]]
  [ rec-clock-bus 1000, now-bus 1001, trig [0 :tr]]
  ;; [bus 0 trig 1]
  (let
      [
       bs   (apply buffer-stream "tmp/openloop.wav" nr-chan "float")
       ;; rec  (disk-recorder [:head group] bs)
       rec  (disk-out bs (in 50 nr-chan) )
       ;; rec  (disk-out bs (in 0 nr-chan) [:head group])
       rec-clock (phasor:ar :trig 1 :end max-phasor-val ) ; start counting immediately
       kr-clock (a2k rec-clock)
       now (a2k (latch:ar rec-clock trig))
       ]
    ;; (defsynth rec []  (disk-out bs (in 0 nr-chan)))
    ;; (rec)
    (send-trig:kr trig 0 kr-clock)
    (out:kr now-bus (* now trig))
    ;; (out:kr now-bus now)
    (out:ar rec-clock-bus rec-clock)
    ;; bs
    ;; rec
    ;; (swap! fsm-state assoc-in [:value :recorder] {:rec-id rec :buf-stream bs})
    ;; (disk-recording-start group "/tmp/openloop.wav" :n-chans nr-chan :samples "float")
    ))

(defn disk-recording-stop
  "Stop system-wide recording. This frees the file and writes the wav headers.
  Returns the path of the file created."
  []
  (if-let [info (:recorder (:value @fsm-state))]
    (do
      (kill disk-rec+count)
      (buffer-stream-close (:buf-stream info))
      (swap! fsm-state assoc-in [:value :recorder] nil)
      (get-in info [:buf-stream :path]))
    (throw (Exception. (str "We where not recording."))))
  )

(defn disk-recording?
  []
  (not (nil? (:recorder (:value @fsm-state)))))

;; (show-graphviz-synth disk-rec+count)
;; (def disk-rec+count-synth (disk-rec+count ))
;; (def play-synth (play ))

;; (-main)
;; (clear-all)
;; (pp-node-tree)


;; (disk-rec+count 0 0)
;; (disk-recording-stop)
;; (swap! fsm-state assoc-in [:value :recorder] nil)
;; (disk-recording?)
