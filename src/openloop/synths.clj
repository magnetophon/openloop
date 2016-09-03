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

(defsynth old-slave-rec
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


(defsynth old-slave-play
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
        ;; rec-clock (phasor:ar :trig 1 :end max-phasor-val ) ; start counting immediately
        rec-clock (sweep:ar 1 SR)
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

;; modes:
;; 0 stop
;; 1 rec
;; 2 play
;; 3 replace  : reeplace the audio @ playhead, wrap around when at the end
;; 4 replace-extend : replace, but when @ the end of the loop, extend it.
;; 5 extend after : start recording when we reach the end of the loop
;; 6 double : souble the length of the loop by copying it
;; 7 cut-mode: toggle the volume

(defn get-mode
  "get the mode of loop i"
  [i]
  ;; (buffer-get modes-buffer i)
  (buf-rd:kr 1 modes-buffer i 0 1)
  )

(defn get-active-loop
  "get the number of the currently active loop"
  []
  (buffer-get active-loop-buffer 0))

(defsynth command-handler
  "receive keyboard, midi, or osc events and turn them into commands for the loopers"
  [mode 0 loop-nr (+ 1 nr-loops)]
  (let [
        prev-active-loop (get-active-loop)
        prev-mode-of-prev-loop (get-mode prev-active-loop)
        new-loop? (not= loop-nr prev-active-loop)
        new-mode? (not= mode prev-mode-of-prev-loop)
        needs-transition? (and new-loop?
                               new-mode?
                               (not= 0 prev-mode-of-prev-loop)
                               (not= 2 prev-mode-of-prev-loop)
                               (not= 7 prev-mode-of-prev-loop))
        transition-mode (select:kr
                         needs-transition?
                         [ prev-mode-of-prev-loop
                          2
                          ])
        prev-mode-bus (+ prev-active-loop mode-bus-base )
        mode-bus (+ loop-nr mode-bus-base)


        ]

    (out:kr prev-mode-bus transition-mode)
    (out:kr mode-bus mode)
    (buf-wr:kr loop-nr active-loop-buffer 0 0)
    (buf-wr:kr mode modes-buffer loop-nr 0 )

    ;; [mode 0 loop-nr [0 :tr]]
    ;; (out:kr mode-bus mode)
    (out:kr loop-nr-bus loop-nr)
    (send-trig:kr (impulse:kr 1) 6 (a2k mode ) )
    ;; (send-trig:kr (impulse:kr 1) 666 (a2k loop-nr ) )
    ))

(show-graphviz-synth command-handler)



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
        ;; master-clock  (wrap:ar (- rec-clock start) 0 length)
        master-clock (select started?
                             [(dc:ar -1)
                              (phasor:ar :trig started?  :end length )])
        ]
    (out:kr length-bus length )
    (out:ar master-clock-bus master-clock )
    )
  )

;; (show-graphviz-synth master-clock)

(defsynth loop-rec
  "record a loop to ram"
  [ rec-clock-bus 42,  in-bus 50, out-bus 70, length-bus 80, which-buf 0, master-clock-bus 44, now-bus 1001, reset [0 :tr]]
  (let [
        now (in:kr now-bus 1)
        new-now? (not= 0 now)
        ;; master-length (in:kr length-bus 1)
        ;; reset (in:kr reset-bus 1)
        ;; rec-clock (in:ar rec-clock-bus 1) ; the disk-clock
        master-clock (in:ar master-clock-bus) ; the master-loop clock
        ;; is recording would be kind of a misnomer, cause we a are always recording. this means that the user has told us that he wants to record
        wants-recording (toggle-ff:kr  new-now?)

        wants-start? (and new-now? (= wants-recording 1)) ;was start pressed?
        ;; wants-stop? (and new-now? (= wants-recording 0)) ; was stop pressed

        started? (set-reset-ff:kr wants-start? reset) ; once start is pressed, stays 1 until we delete the loop

        ;; have-master? (> master-length 0) ; is there a master loop at the moment?
        ;; currently-have-master-length? (> master-length 0) ; is there a master loop at the moment?
        ;; have-master?  (latch:kr currently-have-length? started?) ; was there a master loop when we pressed start?
        ;; have-master?  (select started?
        ;;                       [0
        ;;                        currently-have-master-length?]) ; was there a master loop when we pressed start?
        ;; is-recording (set-reset-ff (and wants-recording have-master) reset)
        ;; first-half? (<= master-clock (/ length 2)) ; are we in the first half?
        ;; master-start (latch:ar rec-clock (= 0 master-clock)) ; get a new start val every time the master passes 0

        ;; actual-start (latch:ar master-start started?) ; when did we start recording
        ;; try-record-start (select started?
        ;;                          [master-start
        ;;                           actual-start])

        reset-rec? (and (= 0 master-clock) (= 0 started?))

        loop-rec-clock (sweep:ar reset-rec? SR)


        ;; is-recording        (tap :my-tap 5 is-recording)
        my-in (in:ar in-bus nr-chan)
        ]
    (buf-wr:ar my-in which-buf loop-rec-clock 0 )
    ;; (send-trig:kr (impulse:kr 1) 66 (a2k started? ) )
    ;; (record-buf:ar my-in which-buf 0 1 0 is-recording 0)
    ;; (out:ar rec-clock-bus rec-clock)
    ))

;; (show-graphviz-synth loop-rec)


(defsynth loop-play
  "play back a slave loop"
  [ in-bus 50, out-bus 70, length-bus 80, rec-clock-bus 42, master-clock-bus 44, now-bus 2000, reset-bus 1002, which-buf 0, reset [0 :tr]]
  (let [

        ;; **************************************************************************************
        ;; input busses
        ;; **************************************************************************************

        now (in:kr now-bus) ; gives the rec-clock time when we hit start/stop
        rec-clock (in:ar rec-clock-bus) ; disk recording clock
        master-clock (in:ar master-clock-bus) ; the master-loop clock
        master-length (in:kr length-bus) ; length of the master loop in samples
        ;; delete? (in:kr reset-bus 1); do we want to delete the loop?
        delete? reset; do we want to delete the loop?
        ;; wants-mode (in:kr mode-bus)
        mode-bus (+ which-buf mode-bus-base)
        mode (in:kr mode-bus)
        loop-nr (in:kr loop-nr-bus)
        ;; which-buf loop-nr

        ;; **************************************************************************************
        ;; intermediate values
        ;; **************************************************************************************

        ;; new-mode? (= which-buf loop-nr)

        ;; mode (latch:kr new-mode? wants-mode)



        new-now? (not= 0 now) ; do we have a new value?
        wants-recording (toggle-ff:kr new-now?) ;; does the user want recording?
        ;; is-recording        (tap :my-tap 5 is-recording)
        wants-start? (and new-now? (= wants-recording 1)) ;was start pressed?
        wants-stop? (and new-now? (= wants-recording 0)) ; was stop pressed

        started? (set-reset-ff:kr wants-start? delete?) ; once start is pressed, stays 1 until we delete the loop
        stopped? (set-reset-ff:kr wants-stop? delete?) ; once stop is pressed, stays 1 until we delete the loop

        currently-have-length? (> master-length 0) ; is there a master loop at the moment?
        have-master?  (latch:kr currently-have-length? started?) ; was there a master loop when we pressed start?
        ;; have-master? (not= 0 master-length) ; is there a master loop?

        master-start (latch:ar rec-clock (= 0 master-clock)) ; get a new start val every time the master passes 0
        ;; first-half? (<= master-clock (/ length 2)) ; are we in the first half?
        ;; gate   Lets signal flow when trig is positive, otherwise holds last input value

        wants-start (latch:kr now started? ) ; the time when we pressed start
        wants-stop (latch:kr now stopped? ) ; the time when we pressed stop
        ;; actual-start (latch:ar master-start started? ) ; when did we start recording
        ;; actual-start (gate:ar master-start (= 0 started?) ) ; when did we start recording
        slave-start (- (latch:ar master-start started?) master-length) ; when did the slave start recording

        ;; try-record-start (select started?
        ;;                          [master-start
        ;;                           actual-start])


        start-offset (- wants-start slave-start) ; how far after the loop-recording started did we press start?
        wants-length  (max 0 (- wants-stop wants-start) ) ; many samples between start and stop
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
        naive-loop-length (select:kr switch-point? [prev-sensible-length next-sensible-length]) ; doesn't do the right thing for short rec periods, hence naive
        corner-case-length (select:kr
                            (> fraction  1.5) ; if we record less then  one and a half a master-length, assume 1 loop
                            [1
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
        ;; loop-length (* master-length (max 1 naive-loop-length))

        next-block? (> rec-clock (+ slave-start loop-length start-offset ))
        ;; next-block? (> rec-clock (+ actual-start loop-length ))
        ;; should-play? stopped?
        should-play? (and next-block? stopped?)


        ;; loop-clock (* should-play?
        ;;               (-
        ;;                (+ start-offset
        ;;                   (wrap:ar (- rec-clock actual-start  start-offset) 0 loop-length))
        ;;                master-length))
        ;; loop-clock (* should-play? (+ start-offset (phasor:ar started? 1 0 loop-length)))
        slave-clock (* should-play?
                       (+
                        (-
                         (wrap:ar
                          (-
                           (+
                            (phasor:ar (and (=  0 master-clock) (= 0  should-play?)) 1 0  loop-length)
                            master-length)
                           start-offset)
                          0 loop-length)
                         master-length)
                        start-offset )
                       )
        new-clock (* stopped? master-clock) ; the clock for playing the master loop

        loop-clock (select have-master?
                           [new-clock ; we are the master, so start playing as soon as we stop recording
                            slave-clock])
        ;; loop-clock new-clock
        ;; 0 loop-length)
        ;; (wrap:ar
        ;;  (- (phasor:ar (and (= 0 master-clock) (= 0  should-play?)) 1 0  loop-length) start-offset
        ;;  0 loop-length))

        ;; replacing? (and replace wants-recording)

        loop-on (or (= 2 mode) (and (= 3 mode) (= 0 wants-recording)))  ; only actually play the loop when we are either in play mode, or we are in replace mode, but not recording.

        sig (* (buf-rd:ar nr-chan which-buf loop-clock 0 1) loop-on)


        ;; **************************************************************************************
        ;; recording
        ;; **************************************************************************************


        my-in (in:ar in-bus nr-chan)

        reset-rec? (and (= 0 master-clock) (= 0 started?))


        ;; modes:
        ;; 0 stop
        ;; 1 rec
        ;; 2 play
        ;; 3 replace  : reeplace the audio @ playhead, wrap around when at the end
        ;; 4 replace-extend : replace, but when @ the end of the loop, extend it.
        ;; 5 extend after : start recording when we reach the end of the loop
        ;; 6 double : souble the length of the loop by copying it
        ;; 7 cut-mode: toggle the volume

        stop-index (dc:ar (+ max-loop-length 1)) ; when stopped, write after the loop: iow don't write.
        rec-index (sweep:ar reset-rec? SR)
        replace-index (select wants-recording
                              [stop-index
                               loop-clock])


        write-index (select:ar mode
                               [
                                stop-index
                                rec-index
                                stop-index
                                replace-index
                                ])
        ;; lengths-buffer-index (select:kr stopped?
        ;;                                 [nr-loops ; if we are not stopped, we don't have a valid length so we write it outside of the buffer. (hope that's safe!! )
        ;;                                  which-buf]) ;otherwise write it to out buffer
        ]
    ;; (send-trig:kr now-bus 0 now-bus)
    (out:ar out-bus sig)
    (buf-wr my-in which-buf write-index 0 )
    (send-trig:kr (impulse:kr 1) 66 (a2k mode ) )
    ;; (send-trig:kr (impulse:kr 1) 666 (a2k wants-mode ) )
    ;; (buf-wr:kr loop-length lengths-buffer lengths-buffer-index 0 )
    ;; (send-trig:kr new-now? 42 (a2k corner-case-length) )
    ;; (send-trig:kr (impulse:kr 1) 42 (a2k prev-sensible-length ) )
    ;; (send-trig:kr (impulse:kr 1) 41 (a2k actual-start ) )

    ))

(show-graphviz-synth loop-play)

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

;; (show-graphviz-synth disk-play)


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
