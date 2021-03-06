(ns openloop.core
  (:gen-class)
  (use [overtone.core])
  ;; afaik I can't require or use these as they are the same ns
  (load "constants")
  (load "boot")
  (load "states")
  (require
   [overtone.sc.machinery.server.connection :as con]
   )
  ;;  ;; [quil.core :as q]
  ;;  [openloop.constants]
  ;;  [overtone.core]
  ;;  [openloop.boot]
  ;;  ;; [openloop.functions]
  )



(defn -main
  "I don't do a whole lot ... yet."
  [& args]
  (boot)
  (init)
  )
;; (ctl play-synth0 :now-bus 1001)

(defn switch-to-i
  "switch the current loop"
  [i]
  (let [player (eval (int-to-play-synth i)) ]


    ;; (ctl loop-master-play-synth  :now-bus 2000)
    ;; (ctl master-clock-synth  :now-bus 2000)
    ;; (ctl loop-rec-synth   :which-buf (eval i))
    ;; (ctl loop-rec-synth   :reset 1)
    ;; (ctl player :which-buf (eval i))

    ;; (dotimes [j nr-loops]
    ;;   (ctl  (int-to-play-synth j)  :replace 0))

    ;; (ctl player :now-bus 1001)
    (ctl command-handler-synth :loop-nr i)
    ;; (ctl play-synth1  :now-bus 1001)
    ;; (ctl slave-rec-synth  :which-buf 1)
    ))

(defn reset-i
  "clear a loop"
  [i]
  (let [player (eval (int-to-play-synth i)) ]
    (clearbuf i)
    (switch-to-i i)
    (ctl player :reset 1)
    (ctl player :replace 0)
    ))

(defn replace-mode-i
  "put the loop in replace mode"
  [i rep]
  (let [player (int-to-play-synth i) ]
    (ctl player  :replace rep)
    (dotimes [j nr-loops]
      (when-not (= i j)
        (ctl  (int-to-play-synth j)  :replace (= 0 rep)))
      )
    ))
(switch-to-i 0)


(on-event "/tr" #(println "event: " % (msg2int %)) ::index-synth)
(remove-event-handler ::index-synth)

(def-loop-player 0)

(buffer-fill! modes-buffer 0 nr-loops 0)
(reset-i 1)
(replace-mode-i 4 1)

(-main)
(pp-node-tree)
(init)
(switch-to-i 0)
(ctl command-handler-synth  :trig 1)
;; (ctl (:rec-id (:recorder (:value @fsm-state)))  :trig 1)
(ctl command-handler-synth  :mode 1)
(ctl command-handler-synth  :mode 2)
(ctl command-handler-synth  :mode 3)
(ctl command-handler-synth  :mode 0)
(switch-to-i 1)
(switch-to-i 2)
(switch-to-i 3)
(switch-to-i 4)
(switch-to-i 5)
(switch-to-i 6)
(switch-to-i 7)
(ctl loop-rec-synth   :reset 1)
(ctl play-synth3   :reset 1)
(def play-i-synth (play-i [:tail play-group] 0))
(kill play-i-synth)
(defsynth play-i
  [i 0]
  (out:ar 70 (buf-rd:ar nr-chan i (sweep:ar 1 SR) 0 1))
  )
@(get-in master-clock-synth [:taps :length ])
@(get-in loop-master-play-synth [:taps :clock ])
@(get-in ram-master-rec-synth [:taps :my-tap])
@(get-in (:rec-id (:recorder (:value @fsm-state))) [:taps :my-tap])
@(get-in loop-master-play-synth [:taps :my-tap])
(swap! fsm-state assoc-in [:value :recorder] nil)
(show-graphviz-synth disk-recorder)


(defsynth foo []
  (let [amp (lin-lin (sin-osc:kr 0.5) -1 1 0 1)
        amp (tap :foo-amp 5 amp)
        snd (sin-osc 440)]
    (out 0 (* amp snd))))
(def f (foo))
(kill foo)


(show-graphviz-synth disk-rec+count)
;; (ctl disk-rec+count-synth  :trig 1)
;; (def disk-rec+count-synth (disk-rec+count ))
(def disk-rec+count-synth (disk-rec+count [:head rec-group]))
;; (def disk-rec+count-synth (disk-rec+count rec-group 1000 1001 trigger ))
(kill disk-rec+count-synth)
(:rec-id (:recorder (:value @fsm-state)))
(:buf-stream  (:recorder (:value @fsm-state)))
(disk-recording?)

(defsynth index-synth [c-bus 0 rate 1]
  "the number of frames on disk"
  (let [
        ;; trigger (impulse:ar (/ SR 2))
        slowtrigger (impulse:kr 1)

        ;; count (stepper:ar trigger :min 0 :max 3.3554432E7 :step 2)
        ;; count (phasor:ar trigger :min 0 :max (* SR 60 60) :step 2)
        count (sweep:ar 1 SR)
        ]
    (send-trig:kr slowtrigger 0 (a2k count))
    ))

(show-graphviz-synth index-synth)
(on-event "/tr" #(println "samples: " (msg2int %) "   hours: " (float (/ (msg2int %) (* SR 60 60)))) ::index-synth)
(on-event "/tr" #(println "samples: " % ) ::index-synth)
(remove-event-handler ::index-synth)

(defn msg2int
  "takes a message and returns the value in integer"
  [msg]
  (int (last (:args msg)) ))


(index-synth)
(kill index-synth)
(clear-all)



;; score of timed OSC commands:
;; http://doc.sccode.org/Classes/Score.html
;; also analysis:
;; http://doc.sccode.org/Guides/Non-Realtime-Synthesis.html
;; // alternatively, provide a pathname to a local git repository:
;; g = Git("/path/to/local/repo");

;; record:

(def bs   (apply buffer-stream "tmp/openloop.wav" nr-chan "float"))
(defsynth rec []  (disk-out bs (in 0 nr-chan) ))
(def rec-synth (rec))
(show-graphviz-synth rec)

(-main)

(comment

  (key-state)
  (index-in-between)
  (lf-saw)
  recording?

  (tap)
  (sample-dur)
  (control-dur)

  (disk-out out-buf (in 0 2)))
(buffer-save buf \"~/Desktop/foo.wav\" :header \"aiff\" :samples \"int32\" :n-frames 10
             :start-frame 100)
(buffer-stream)

(show-graphviz-synth)

;; play:
(def foo1 (load-sample "~/gun1.wav" :start 9000 :size 4000))
(stereo-partial-player foo1 :loop? true)
(kill-player)
;; (buffer-cue "~/gun2.wav" :start (* 3 44100) :size max-loop-length))
(buffer-cue)


;; (init)
(kill-server)
(server-connected?)
;; (pp-node-tree)
;; (pprint nr-chan)
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; define some functions
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn set-up-fsm
  "set up everything needed for the finite state machine"
  []

  )

;; (:FX (first (:all-loops (first (:undo-stack (:saved-state (:value @fsm-state)))))))
(:value @fsm-state)
(:state @fsm-state)
(pprint (server-status))
(swap! fsm-state fsm/fsm-event :loop-btn-down)
(kill-server)
(swap! fsm-state assoc-in [:value :booted] true)
;; (swap! fsm-state fsm/fsm-event :loop-btn-up)
;; (swap! fsm-state fsm/fsm-event :tap)
;; (swap! fsm-state fsm/fsm-event :timeout)
;; (swap! fsm-state fsm/fsm-event :other-loop-btn)





( quil.applet/with-applet openloop.core/loop
 (q/background 000)
 )
(server-info)
;; (load openloop.constants)
;; (connect-external-server)
;; (pp-node-tree)
(load-s)

(init)
(start-master)
(stop-master)
(ctl s-rec-synth1 :start 1)
(stop-slave)


(show-graphviz-synth master-play)


(comment

  (show-graphviz-synth loop-play3)
  (clear-all)
  (control-bus-monitor)

  (server-num-buffers)
  (server-num-audio-buses)

  (opp master-rec)
  (stop-all)
  (status)
  (disk-in)
  (v-disk-in)
  (buffer-read)
  (buffer-stream)
  (buffer-alloc-read path start n-frames)
  )

(buffer-save)
