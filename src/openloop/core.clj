(ns openloop.core
  (require [quil.core :as q])
  )

(use 'overtone.core)
;; (use 'overtone.studio.scope)
(connect-external-server)
(server-info)
(pp-node-tree)

;; set some constants
(def SR (:sample-rate (server-info)))
(def max-loop-seconds 30)
(def max-loop-samples (* max-loop-seconds SR))
(def nr-chan 2)
(def nr-loops 8)

(println (status))

;; ;; create some buffers
;; ;; (dotimes [i   nr-loops]
;; ;;   (let [bufname (clojure.string/join ["buffer" (str i)])]
;; (for [i (range 1 8)]
;;     (eval `(def ~(symbol (str "buffer" i)) (buffer max-loop-samples nr-chan (str "buffer" i))))
;;     ;; (def  (buffer max-loop-samples nr-chan (str bufname)))
;;     ))


(defsynth input
  "routs the input to the output and the recorders"
  [amp 1 rec-bus 50 dir-bus 60]
  ;; (def my-in (sound-in[0 1]))
  (def my-in (sound-in (range nr-chan)))
  ;; (def my-in (in:ar 0 nr-chan ))
  (def dir-sig (* my-in amp))
  (out rec-bus my-in)
  (out dir-bus dir-sig))

(defsynth master-rec
  "records the master loop"
  [start [0 :tr] stop [0 :tr] timer-bus 40 in-bus 50 loop 1 which-buf 0]
  (def start )
  (def is-recording (set-reset-ff:kr start stop))
  (def my-in (in:ar in-bus nr-chan))
  (def my-timer (timer:kr (+ start stop)))
  (out:kr timer-bus my-timer)
  (record-buf:ar my-in which-buf 0 1 0 is-recording loop))

(defsynth slave-rec
  "records the slave loops"
  [start [0 :tr] stop [0 :tr] in-bus 50 loop 1 which-buf 0]
  (def is-recording (set-reset-ff:kr start stop))
  (def my-in (in:ar in-bus nr-chan))
  (record-buf:ar my-in which-buf 0 1 0 is-recording loop))

(setup)
(def m-play-synth (master-play [:head play-master-group]))



(defsynth master-play
  "plays back the master loop"
  [rate 1 nr-bars 1 which-buf 0 amp 1 atk-thres 0.002 timer-bus 40 length-bus 80 downbeat-bus 90 nr-bars-bus 100 out-bus 70 ]
  (def my-timer (mod (in:kr timer-bus) max-loop-seconds ))
  ;; (def start (select:kr (= 0 rate) [(max 0 (/ (index-in-between:kr which-buf atk-thres) nr-chan)) 0]))
  (def start (max 0 (/ (index-in-between:kr which-buf atk-thres) nr-chan)))
  ;; (def start (index-in-between:kr which-buf atk-thres))
  ;; (def start (max (in:kr soundstart-bus) 0))
  ;; (if (= 0 rate)
  ;;   (def start 0)
  ;;   (def start (max 0 (/ (index-in-between:kr which-buf atk-thres) nr-chan)))
  ;;   )
  (def end (* my-timer SR))
  (def length (- end start))
  (def downbeat (impulse:kr (/ (* nr-bars SR) length)))
  (send-trig:kr downbeat)
  (def buf-start (pulse-divider:kr downbeat nr-bars (- nr-bars 1)))
  (def phs (+ start (sweep:ar buf-start SR)))
  (def sig (* (buf-rd:ar nr-chan which-buf phs 1 1) amp))
  (out:kr length-bus length)
  (out:kr downbeat-bus downbeat)
  (out:kr nr-bars-bus nr-bars)
  ;; (out:ar out-bus (sin-osc 220)))
  ;; (out:ar out-bus (silent:ar)))
  (def test (+ (* 0.5 (decay downbeat 0.1) (sin-osc 880)) sig))
  ;; (out:ar out-bus test))
  (out:ar out-bus sig))

(defsynth pinky [trigme 0]
  (let [src1 (sin-osc 220)
        ]
    (* 0.5 (decay trigme 0.1) src1)))
;; (* (decay (impulse:kr 1) 0.1) src1)))

(setup)
(start-master)
(stop-master)
(ctl s-rec-synth1 :start 1)
(stop-slave)

;; (def s-play-synth1 (slave-play3))

(show-graphviz-synth slave-play3)
(show-graphviz-synth pingme)
(clear-all)

(control-bus-monitor)
(setup)
(def s-play-synth1 (slave-play [:tail play-master-group] :which-buf 1 ))
(def s-play-synth2 (slave-play11))
(pp-node-tree)

(defsynth slave-play
  "play back a slave loop"
  [rate 1 jump [0 :tr] length-mul 1 which-buf 0 del 0 atk-thres 0.002 amp 1 nr-bars-bus 100 downbeat-bus 90 length-bus 80 out-bus 70]
  (def length (* (in:kr length-bus) length-mul))
  (def nr-bars (in:kr nr-bars-bus))
  (def downbeat (in:kr downbeat-bus))
  (def start (max 0 (floor (/ (index-in-between:kr which-buf atk-thres) nr-chan))))
  ;; (def start 0)
  (def end (+ start length))
  (def my-sync (set-reset-ff:kr downbeat 0))
  (def jump-trig (env-gen:kr (env-adsr 0.001 0.001 0 1 1 0) my-sync))
  ;; (def del 0)
  ;; (def phs (+ 0 (sweep:ar 0 SR)))
  (def phs (select:ar my-sync [(dc:ar 0) (phasor:ar (+ jump-trig jump) (buf-rate-scale:kr which-buf) start end start)]))
  (def sig (buf-rd:ar nr-chan which-buf phs 1 1))
  ;; (def test (+ (* 0.5 (decay downbeat 0.1) (sin-osc 880)) 0))
  (def test (+ (* 0.5 (decay downbeat 0.1) (sin-osc 880)) sig))
  ;; (out:ar out-bus (sin-osc 220)))
  (out:ar out-bus test))

(defsynth output
  "mix everything and send it out"
  [rec-bus 70 dir-bus 60 dir-amp 1 main-amp 1 out-bus 0]
  (def rec-sig (in:ar rec-bus nr-chan))
  (def dir-sig (in:ar dir-bus nr-chan))
  (def out-sig (* main-amp (+ dir-sig rec-sig)))
  (out:ar out-bus out-sig))

(defn groups
  "define groups"
  []
  (def in-group (group "in-group"))
  (def rec-group (group "rec-group" :after in-group))
  (def play-master-group (group "play-master-group" :after rec-group))
  (def play-slave-group (group "play-slave-group" :after play-master-group))
  (def out-group (group "out-group" :after play-slave-group)))

(defsynth pinger []
  (let [freq 440
        src1 (sin-osc freq)
        t1 (in:kr 90)]
    (* (decay t1 0.1) src1)))

(defn setup
  "initialise"
  []
  ;; (group-deep-clear 7)
  (clear-all)
  (groups)
  (defonce buffer0 (buffer max-loop-samples nr-chan buffer0))
  (defonce buffer1 (buffer max-loop-samples nr-chan buffer1))
  (defonce buffer2 (buffer max-loop-samples nr-chan buffer2))
  (defonce buffer3 (buffer max-loop-samples nr-chan buffer3))
  (defonce buffer4 (buffer max-loop-samples nr-chan buffer4))
  (defonce buffer5 (buffer max-loop-samples nr-chan buffer5))
  (defonce buffer6 (buffer max-loop-samples nr-chan buffer6))
  (defonce buffer7 (buffer max-loop-samples nr-chan buffer7))

  (buffer-fill! buffer0 0)
  ;; hack around index-in-between xruns:
  ;; (buffer-set! buffer0 0 0.003)
  (buffer-fill! buffer1 0)
  (buffer-fill! buffer2 0)
  (buffer-fill! buffer3 0)
  (buffer-fill! buffer4 0)
  (buffer-fill! buffer5 0)
  (buffer-fill! buffer6 0)
  (buffer-fill! buffer7 0)

  ;; (dotimes [i   nr-loops]
  ;;   (let [bufname (str "buffer" i)]
  ;;   (buffer-fill! (symbol bufname) 0)
  ;;     ))

  (def in-synth (input [:head in-group]))
  (def out-synth (output [:head out-group]))
  (def m-rec-synth (master-rec [:head rec-group]))
  (def s-rec-synth1 (slave-rec [:tail rec-group] :which-buf 1 ))
  ;; (def s-rec-synth1 (slave-rec [:head rec-group]))
  ;; can't do this unless you use
  ;; hack around index-in-between xruns:
  ;; see above
  ;; (def m-play-synth (master-play [:head play-master-group]))
  (pp-node-tree))


(defn start-master
  "start recording the master"
  []
  (ctl m-rec-synth :start 1)
  ;; (ctl m-rec-synth :start 0)
  )

(defn stop-master
  "stop the master record and start playing it"
  []
  (ctl m-rec-synth :stop 1)
  (def m-play-synth (master-play [:head play-master-group]))
  )

(defn stop-slave
  "stop the slave record and start playing it"
  []
  (ctl s-rec-synth1 :stop 1)
  (def s-play-synth1 (slave-play [:head play-slave-group] :which-buf 1 ))
  )

(setup)
(start-master)
(stop-master)
(ctl s-rec-synth1 :start 1)
(stop-slave)

(pinky (impulse:ar 1))

(def s-play-synth1 (slave-play [:head play-slave-group] :which-buf 1 ))
(clear-all)
(pp-node-tree)
(def s-play-synth1 (slave-play))
(def pingster (pinky))

(show-graphviz-synth master-play)


;; ******************************************************************************
;;              GUI
;; ******************************************************************************


(defsynth metro-synth [c-bus 0 rate 1]
  (let [trigger (impulse:kr rate)
        count (stepper:kr trigger :min 1 :max 4)]
    (send-trig:kr trigger count)
    (out:kr c-bus trigger)))

(def downbeatAtom  (atom nil))

(on-event "/tr"
          #(do
             (println "trig: " %)
             (reset! downbeatAtom 1))
          ::metro-synth0)

(metro-synth)
(clear-all)


(defn setup []
  ;; (q/background 200)
  (q/frame-rate 3))                 ;; Set the background colour to

(println (str @downbeatAtom))
(defn draw []
  (q/background 100)
  (q/text "test" 10 20)
  (when (= @downbeatAtom 1)
    (do
      (q/text (str @downbeatAtom) 10 40)
      (q/background 255)
      (reset! downbeatAtom 0))
    )
  )

(q/defsketch loop                  ;; Define a new sketch named example
  :title "Oh so much looping"    ;; Set the title of the sketch
  :settings #(q/smooth 2)             ;; Turn on anti-aliasing
  :setup setup                        ;; Specify the setup fn
  :draw draw                          ;; Specify the draw fn
  :size [300 300]                     ;; You struggle to beat the golden ratio
  :setup #(q/background 100)
  ;; :settings q/no-loop
  )

(comment

  (dotimes [i   nr-loops]
    (let [bufname (str "buffer" i)]
      ;; (buffer-fill! (symbol bufname) 0)
      (println (symbol bufname))))


  (buffer-fill! buffer0 0)
  (buffer-free buffer0)

  (stop-master)


  (buffer-free 0)

  (server-num-buffers)
  (server-num-audio-buses)
  (buffer-fill! 0 0)

  (def pingr-synth (pinger [:tail play-slave-group]))

  (free 1 m-rec-synth)
  (stop)

  (opp master-rec)
  (free 1 44))

(buffer1)
(setup)

(stop-all)
(status)

(stop)
(in-group)

(status)
(buffer1)
(buffer-fill! buffer1 0)
(ensure-buffer-active! 1)
(assert (buffer?  "a-bufname"))

(buffer-info 9999)

(buffer max-loop-samples nr-chan "a-bufname")
