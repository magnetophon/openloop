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
        ;; kr-clock (tap :my-tap 5 kr-clock)
        now (a2k (latch:ar rec-clock trig))
        audio-in (in in-bus nr-chan)]
    ;; (send-trig:kr trig 0 kr-clock)
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
  [length-bus 80, rec-clock-bus 42  master-clock-bus 44, now-bus 1001, reset-bus 1002]
  (let [
        now (in:kr now-bus 1)
        new-now? (not= 0 now)
        reset (in:kr reset-bus 1)
        ;; is-recording (toggle-ff:kr new-now?)
        ;; first-recording (set-reset-ff:kr new-now?)
        ;; stop? (set-reset-ff:kr (and new-now? (= is-recording 0)))
        ;; ;; start? (and new-now? (= first-recording 1))
        ;; start (latch:kr now first-recording )
        ;; stop (latch:kr now stop? )
        ;; length (max (- stop start) 0 )
        is-recording (toggle-ff:kr new-now?)
        ;; is-recording        (tap :my-tap 5 is-recording)
        start? (and new-now? (= is-recording 1))
        stop? (and new-now? (= is-recording 0))
        started? (set-reset-ff:kr start? reset) ; once start is pressed, stays 1 until we delete the loop
        stopped? (set-reset-ff:kr stop? reset) ; once stop is pressed, stays 1 until we delete the loop
        start (latch:kr now started? )
        stop (latch:kr now stopped? )
        length (max (- stop start) 0 )
        ;; tappers (tap :start 5 start)
        ;; tappert (tap :stop 5 stop)
        ;; tapperl (tap :length 5 length)
        rec-clock (in:ar rec-clock-bus 1)
        ;; first-start (latch first-recording now)
        master-clock  (wrap:ar (- rec-clock start) 0 length)
        ]
    (out:kr length-bus length )
    (out:ar master-clock-bus master-clock )
    )
  )

(show-graphviz-synth master-clock)


(defsynth ram-master-rec
  "record a loop to ram"
  [ in-bus 50, out-bus 70, which-buf 0, master-clock-bus 44, now-bus 1001, reset-bus 1002]
  (let [
        now (in:kr now-bus 1)
        new-now? (not= 0 now)
        reset (in:kr reset-bus 1)
        is-recording (set-reset-ff:kr new-now? reset)
        ;; is-recording        (tap :my-tap 5 is-recording)
        my-in (in:ar in-bus nr-chan)
        ]
    (record-buf:ar my-in which-buf 0 1 0 is-recording 0)
    ;; (out:ar rec-clock-bus rec-clock)
    ))

(defsynth ram-slave-rec
  "record a loop to ram"
  [ rec-clock-bus 42,  in-bus 50, out-bus 70, length-bus 80, which-buf 7, master-clock-bus 44, now-bus 1001, reset-bus 1002]
  (let [
        now (in:kr now-bus 1)
        new-now? (not= 0 now)
        ;; length (in:kr length-bus 1)
        reset (in:kr reset-bus 1)
        rec-clock (in:ar rec-clock-bus 1) ; the disk-clock
        master-clock (in:ar master-clock-bus) ; the master-loop clock
        ;; have-master (not= 0 length) ; is there a master loop?
        ;; is recording would be kind of a misnomer, cause we a are always recording. this means that the user has told us that he wants to record
        wants-recording (toggle-ff:kr  new-now?)

        wants-start? (and new-now? (= wants-recording 1)) ;was start pressed?
        ;; wants-stop? (and new-now? (= wants-recording 0)) ; was stop pressed

        started? (set-reset-ff:kr wants-start? reset) ; once start is pressed, stays 1 until we delete the loop

        ;; is-recording (set-reset-ff (and wants-recording have-master) reset)
        ;; first-half? (<= master-clock (/ length 2)) ; are we in the first half?
        master-start (latch:ar rec-clock (= 0 master-clock)) ; get a new start val every time the master passes 0
        ;; start (select:ar first-half? [master-start (+ master-start length )]) ; the starting point of the loop
        ;; extend-clock (min max-loop-length (- rec-clock start))
        ;; slave-clock (select:ar is-recording [master-clock extend-clock])
        ;; gate:   Lets signal flow when trig is positive, otherwise holds last input value
        actual-start (gate:ar master-start (= 0 started?) ) ; when did we start recording

        ;; start (gate:ar master-start (= 0 is-recording))

        slave-clock (- rec-clock actual-start)
        ;; is-recording        (tap :my-tap 5 is-recording)
        my-in (in:ar in-bus nr-chan)
        ]
    (buf-wr:ar my-in which-buf slave-clock 0 )
    ;; (record-buf:ar my-in which-buf 0 1 0 is-recording 0)
    ;; (out:ar rec-clock-bus rec-clock)
    ))

(show-graphviz-synth ram-slave-rec)

(defsynth loop-master-play
  "play back a master loop"
  [ in-bus 50, out-bus 70, which-buf 0, rec-clock-bus 42, master-clock-bus 44, now-bus 1001, reset-bus 1002]
  (let [
        now (in:kr now-bus 1)
        master-clock (in:ar master-clock-bus) ; the master-loop clock
        reset (in:kr reset-bus 1)
        new-now? (not= 0 now)
        is-recording (toggle-ff:kr new-now?)
        ;; is-recording        (tap :my-tap 5 is-recording)
        start? (and new-now? (= is-recording 1))
        stop? (and new-now? (= is-recording 0))
        started? (set-reset-ff:kr start? reset) ; once start is pressed, stays 1 until we delete the loop
        stopped? (set-reset-ff:kr stop? reset) ; once stop is pressed, stays 1 until we delete the loop
        start (latch:kr now started? )
        stop (latch:kr now stopped? )
        length (max (- stop start) 0 )
        ;; tapperl (tap :length 5 (a2k length ) )
        ;; master-clock (phasor:ar :trig stop? :rate 1 :end max-phasor-val )
        rec-clock (in:ar rec-clock-bus 1)
        loop-clock (* stopped? master-clock)
        ;; loop-clock (* stopped? (wrap:ar (- rec-clock start) 0 length))
        ;; loop-clock (* (= is-recording 0) (wrap:ar (- rec-clock start) 0 length))
        ;; loop-clock (* (= is-recording 0) (wrap:ar master-clock 0 length))
        ;; tapper (tap :clock 5 (a2k loop-clock) )
        ;; loop-clock (* (= is-recording 0) (wrap:ar (- master-clock start) 0 length))
        ;; my-in (in:ar in-bus nr-chan)
        ;; buf (record-buf:ar my-in which-buf 0 1 0 is-recording 0)
        ;; buf (disk-load start length)
        sig (buf-rd:ar nr-chan which-buf loop-clock 0 1)
        ]
    ;; (send-trig:kr now-bus 0 now-bus)
    (out:ar out-bus sig)
    ))

(show-graphviz-synth loop-master-play)

(gate)

(defsynth loop-slave-play
  "play back a slave loop"
  [ in-bus 50, out-bus 70, length-bus 80, which-buf 7, rec-clock-bus 42, master-clock-bus 44, now-bus 2000, reset-bus 1002]
  (let [

        ;; **************************************************************************************
        ;; input busses
        ;; **************************************************************************************

        now (in:kr now-bus 1) ; gives the rec-clock time when we hit start/stop
        rec-clock (in:ar rec-clock-bus 1) ; disk recording clock
        master-clock (in:ar master-clock-bus) ; the master-loop clock
        master-length (in:kr length-bus 1) ; length of the master loop in samples
        delete? (in:kr reset-bus 1); do we want to delete the loop?

        ;; **************************************************************************************
        ;; intermediate values
        ;; **************************************************************************************

        new-now? (not= 0 now) ; do we have a new value?
        wants-recording (toggle-ff:kr new-now?) ;; does the user want recording?
        ;; is-recording        (tap :my-tap 5 is-recording)
        wants-start? (and new-now? (= wants-recording 1)) ;was start pressed?
        wants-stop? (and new-now? (= wants-recording 0)) ; was stop pressed

        started? (set-reset-ff:kr wants-start? delete?) ; once start is pressed, stays 1 until we delete the loop
        stopped? (set-reset-ff:kr wants-stop? delete?) ; once stop is pressed, stays 1 until we delete the loop


        master-start (latch:ar rec-clock (= 0 master-clock)) ; get a new start val every time the master passes 0
        ;; first-half? (<= master-clock (/ length 2)) ; are we in the first half?
        ;; gate   Lets signal flow when trig is positive, otherwise holds last input value

        wants-start (latch:ar now started? ) ; the time when we pressed start
        wants-stop (latch:ar now stopped? ) ; the time when we pressed stop
        ;; actual-start (latch:ar master-start started? ) ; when did we start recording
        actual-start (gate:ar master-start (= 0 started?) ) ; when did we start recording

        start-offset (- wants-start actual-start) ; how far after the loop-recording started did we press start?
        wants-length  (max (- wants-stop wants-start) 0 ) ; many samples between start and stop
        ;; **************************************************************************************
        ;; this block has lengths expressed in nr of master loops
        ;; **************************************************************************************
        fraction (/ wants-length master-length) ; the wanted length expressed in nr of master loops

        ;; the actual loop length should always be a multiple of the master loop length.
        ;; if more than 2, we round to the nearest power of 2.
        ;; so the possible lengths are:
        ;; 0 1 2 4 8 16 32 64 and so on.
        ;; (pow (round (pow fraction 1/2)) 2) doesn't switch in the middle of the possible values, cause it switches in the log domain
        log2fraction (log2 fraction)
        prev-sensible-length  (pow 2 (floor log2fraction))
        next-sensible-length  (pow 2 (ceil log2fraction))
        switch-point? (> fraction (/ (+ prev-sensible-length next-sensible-length) 2))
        naive-loop-length (select switch-point? [prev-sensible-length next-sensible-length]) ; doesn't do the right thing for short rec periods, hence naive
        corner-case-length (select
                            (> fraction  0.5) ; if we record less then half a master-length, assume it was a fluke,
                            [0
                             naive-loop-length
                             ])
        ;; todo: make a version that goes:
        ;; 0 1 2 3 4 6 8 12 16 24 32 48
        ;; or:
        ;; 0 1 3 6 12 24 48
        ;; the last one can maybe be done by scaling/multiplying the fraction before and after the calculation?

        ;; **************************************************************************************
        ;; back to samples
        ;; **************************************************************************************

        loop-length (* master-length corner-case-length)

        next-block? (>= rec-clock (+ actual-start loop-length ))
        should-play? (and next-block? (= wants-recording 0))

        loop-clock (* should-play? (+ start-offset (wrap:ar (- rec-clock actual-start) 0 loop-length)))

        ;; tapperl (tap :length 5 (a2k length ) )
        ;; master-clock (phasor:ar :trig stop? :rate 1 :end max-phasor-val )
        ;; loop-clock (* (= is-recording 0) (wrap:ar master-clock 0 length))
        ;; tapper (tap :clock 5 (a2k loop-clock) )
        ;; loop-clock (* (= is-recording 0) (wrap:ar (- master-clock start) 0 length))
        ;; my-in (in:ar in-bus nr-chan)
        ;; buf (record-buf:ar my-in which-buf 0 1 0 is-recording 0)
        ;; buf (disk-load start length)
        sig (buf-rd:ar nr-chan which-buf loop-clock 0 1)
        ;; sig (dc:ar 42)
        ]
    ;; (send-trig:kr now-bus 0 now-bus)
    (out:ar out-bus sig)
    ;; (send-trig:kr new-now? 42 (a2k corner-case-length) )
    (send-trig:kr (impulse:kr 1) 42 (a2k prev-sensible-length ) )
    ;; (send-trig:kr (impulse:kr 1) 41 (a2k actual-start ) )

    ))

(show-graphviz-synth loop-slave-play)

(defsynth disk-play
  "play back a slave loop"
  [ in-bus 50, out-bus 70, which-buf 7, master-clock-bus 44, now-bus 1001]
  (let [
        now (in:kr now-bus 1)
        new-now? (not= 0 now)
        is-recording (toggle-ff:kr new-now?)
        start (latch:kr now (and new-now? (= is-recording 1)) )
        ;; start        (tap :my-tap 5 start)
        stop (latch:kr now (and new-now? (= is-recording 0)) )
        length (- stop start)
        master-clock (in:ar master-clock-bus)
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
