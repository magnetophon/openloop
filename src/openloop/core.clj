(ns openloop.core
  ;; (require [quil.core :as q])
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
  [t_start 0 t_stop 0 timer-bus 40 in-bus 50 loop 0 which-buf 0]
  (def is-recording (set-reset-ff:kr t_start t_stop))
  (def my-in (in:ar in-bus nr-chan))
  (def my-timer (timer:kr (+ t_start t_stop)))
  (out:kr timer-bus my-timer)
  (record-buf:ar my-in which-buf 0 1 0 is-recording loop))

(defsynth slave-rec
  "records the slave loops"
  [t_start 0 t_stop 0 in-bus 50 loop 0 which-buf 0]
  (def is-recording (set-reset-ff:kr t_start t_stop))
  (def my-in (in:ar in-bus nr-chan))
  (record-buf:ar my-in which-buf 0 1 0 is-recording loop))

(defsynth master-play
  "plays back the master loop"
  [rate 1 nr-bars 1 which-buf 0 amp 1 atk-thres 0.002 timer-bus 40 length-bus 80 downbeat-bus 90 nr-bars-bus 100 out-bus 70 t_test 0]
  (def my-timer (mod (in:kr timer-bus) max-loop-seconds))
  (def start (max 0 (/ (index-in-between:kr which-buf atk-thres) nr-chan)))
  (def end (* my-timer SR))
  (def length (- end start))
  ;; (def nr-bars (in:kr nr-bars-bus))
  (def downbeat (impulse:kr (/ (* nr-bars SR) length)))
  (def buf-start (pulse-divider:kr downbeat nr-bars (- nr-bars 1)))
  (def phs (+ start (sweep:ar buf-start SR)))
  (def pingme
    (let [freq 440
          src1 (sin-osc freq)
          t1 downbeat]
      (* (decay t1 0.01) src1)))
  (def sig (+ t_test (* (buf-rd:ar nr-chan which-buf phs 1 1) amp)))
  ;; (def sig (* (buf-rd:ar nr-chan which-buf phs 1 1) amp))
  (out:kr length-bus length)
  (out:kr downbeat-bus downbeat)
  (out:kr nr-bars-bus nr-bars)
  (out:ar out-bus sig))

(defsynth slave-play
  "play back a slave loop"
  [rate 1 t_jump 0 length-mul 1 which-buf 0 del 0 atk-thres 0.02 amp 1 nr-bars-bus 100 downbeat-bus 90 length-bus 80 out-bus 70]
  (def length (* (in:kr length-bus) length-mul))
  (def nr-bars (in:kr nr-bars-bus))
  (def downbeat (in:kr downbeat-bus))
  (def start (max 0 (/ (index-in-between:kr which-buf atk-thres) nr-chan)))
  (def end (+ start length))
  (def my-sync (set-reset-ff:kr downbeat 0))
  (def jump-trig (env-gen:kr (env-adsr 0.001 0.001 0 1 1 0) my-sync))
  (def del 0)
  (def phs (select:ar my-sync [(silent:ar) (phasor:ar (+ jump-trig t_jump) (buf-rate-scale:kr which-buf) start end start)]))
  (def sig (buf-rd:ar nr-chan which-buf phs 1 1))
  (out:ar out-bus sig))

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
  (defonce in-group (group "in-group"))
  (defonce rec-group (group "rec-group" :after in-group))
  (defonce play-master-group (group "play-master-group" :after rec-group))
  (defonce play-slave-group (group "play-slave-group" :after play-master-group))
  (defonce out-group (group "out-group" :after play-slave-group)))

(defsynth pinger []
  (let [freq 440
        src1 (sin-osc freq)
        t1 (in:kr 90)]
    (* (decay t1 0.1) src1)))

(defn setup
  "initialise"
  []
  (group-deep-clear 7)
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
  (pp-node-tree))

(def in-synth (input [:head in-group]))
(def out-synth (output [:head out-group]))

(setup)
(start-master)
(stop-master)
(ctl m-play-synth :t_test 0)

(group-deep-clear 7)
(pp-node-tree)

(comment

  (dotimes [i   nr-loops]
    (let [bufname (str "buffer" i)]
      ;; (buffer-fill! (symbol bufname) 0)
      (println (symbol bufname))))


  (buffer-fill! buffer0 0)
  (buffer-free buffer0)

  (stop-master)

  (show-graphviz-synth master-play)

  (buffer-free 0)

  (server-num-buffers)
  (server-num-audio-buses)
  (buffer-fill! 0 0)

  (def pingr-synth (pinger [:tail play-slave-group]))

  (free 1 m-rec-synth)
  (stop)

  (opp master-rec)
  (free 1 44))

(defn start-master
  "start recording the master"
  []
  (ctl m-rec-synth :t_start 1)
  (ctl m-rec-synth :t_start 0))

(defn stop-master
  "stop the master record and start playing it"
  []
  (ctl m-rec-synth :t_stop 1)
  (ctl m-rec-synth :t_stop 0)
  (def m-play-synth (master-play [:head play-master-group])))

()

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
