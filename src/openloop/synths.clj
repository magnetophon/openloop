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


;; (defonce __DISKRECORDER__
(defsynth disk-recorder
  "record a file and start counting frames, outputting them on trigger"
  [out-buf 0, rec-clock-bus 42, in-bus 50, now-bus 1001, trig [0 :tr]]
  (let [
        rec-clock (phasor:ar :trig 1 :end max-phasor-val ) ; start counting immediately
        ;; rec-clock (sweep:ar 1 SR)
        kr-clock (a2k rec-clock)
        kr-clock (tap :my-tap 5 kr-clock)
        now (a2k (latch:ar rec-clock trig))
        audio-in (in in-bus nr-chan)]
    (send-trig:kr trig 0 kr-clock)
    (out:kr now-bus (* now trig))
    ;; (out:ar rec-clock-bus (dc:ar 42))
    ;; (out:ar rec-clock-bus audio-in)
    (out:ar rec-clock-bus rec-clock)
    ;; (disk-out out-buf audio-in)
    )
  )
;; )

;; (show-graphviz-synth disk-recorder)

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

(defn disk-load
  "load a loop from disk"
  [start  length]
  (def buf (load-sample "/tmp/openloop.wav" :start start :size length))
  )

;; (disk-load 100 1000)
(defsynth master-clock
  "if we have no loops running, define the new master length"
  [length-bus 80 , masterclock-bus 44, now-bus 1001]
  (let [
        now (in:kr now-bus 1)
        new-now? (not= 0 now)
        is-recording (toggle-ff:kr new-now?)
        first-recording (set-reset-ff:kr new-now?)
        stop? (set-reset-ff:kr (and new-now? (= is-recording 0)))
        ;; start? (and new-now? (= first-recording 1))
        start (latch:kr now first-recording )
        stop (latch:kr now stop? )
        length (max (- stop start) 0 )
        ;; tappers (tap :start 5 start)
        ;; tappert (tap :stop 5 stop)
        ;; tapperl (tap :length 5 length)
        ;; rec-clock (in:ar rec-clock-bus 1)
        ;; first-start (latch first-recording now)
        ;; master-clock  (wrap:ar (- rec-clock start) 0 length)
        ]
    (out:kr length-bus length )
    )
  )

(show-graphviz-synth master-clock)


(defsynth ram-rec
  "record a loop to ram"
  [ in-bus 50, out-bus 70, which-buf 7, masterclock-bus 44, now-bus 1001]
  (let [
        now (in:kr now-bus 1)
        new-now? (not= 0 now)
        is-recording (toggle-ff:kr new-now?)
        ;; is-recording        (tap :my-tap 5 is-recording)
        my-in (in:ar in-bus nr-chan)
        ]
    (record-buf:ar my-in which-buf 0 1 0 is-recording 0)
    ;; (out:ar rec-clock-bus rec-clock)
    ))

(show-graphviz-synth ram-rec)

(defsynth loop-play
  "play back a slave loop"
  [ in-bus 50, out-bus 70, which-buf 7, rec-clock-bus 42, now-bus 1001]
  (let [
        now (in:kr now-bus 1)
        new-now? (not= 0 now)
        is-recording (toggle-ff:kr new-now?)
        ;; is-recording        (tap :my-tap 5 is-recording)
        stop? (and new-now? (= is-recording 0))
        start? (and new-now? (= is-recording 1))
        start (latch:kr now start? )
        stop (latch:kr now stop? )
        length (max (- stop start) 0 )
        ;; tapperl (tap :length 5 (a2k length ) )
        ;; master-clock (phasor:ar :trig stop? :rate 1 :end max-phasor-val )
        rec-clock (in:ar rec-clock-bus 1)
        loop-clock (* (= is-recording 0) (wrap:ar (- rec-clock start) 0 length))
        ;; loop-clock (* (= is-recording 0) (wrap:ar master-clock 0 length))
        tapper (tap :clock 5 (a2k loop-clock) )
        ;; loop-clock (* (= is-recording 0) (wrap:ar (- master-clock start) 0 length))
        ;; my-in (in:ar in-bus nr-chan)
        ;; buf (record-buf:ar my-in which-buf 0 1 0 is-recording 0)
        ;; buf (disk-load start length)
        sig (buf-rd:ar nr-chan which-buf loop-clock 0 1)
        ]
    ;; (send-trig:kr now-bus 0 now-bus)
    (out:ar out-bus sig)
    ))

(show-graphviz-synth loop-play)

(defsynth disk-play
  "play back a slave loop"
  [ in-bus 50, out-bus 70, which-buf 7, masterclock-bus 44, now-bus 1001]
  (let [
        now (in:kr now-bus 1)
        new-now? (not= 0 now)
        is-recording (toggle-ff:kr new-now?)
        start (latch:kr now (and new-now? (= is-recording 1)) )
        ;; start        (tap :my-tap 5 start)
        stop (latch:kr now (and new-now? (= is-recording 0)) )
        length (- stop start)
        master-clock (in:ar masterclock-bus)
        loop-clock (* (= is-recording 0) (wrap:ar master-clock 0 length))
        ;; my-in (in:ar in-bus nr-chan)
        ;; buf (record-buf:ar my-in which-buf 0 1 0 is-recording 0)
        ;; buf (disk-load start length)
        sig (buf-rd:ar nr-chan which-buf loop-clock 0 1)
        ]
    ;; (send-trig:kr now-bus 0 now-bus)
    (out:ar out-bus sig)
    ))

(show-graphviz-synth disk-play)


(defn disk-recording-stop
  "Stop system-wide recording. This frees the file and writes the wav headers.
  Returns the path of the file created."
  []
  (if-let [info (:recorder (:value @fsm-state))]
    (do
      ;; (kill disk-rec+count)
      (kill (:rec-id info))
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
