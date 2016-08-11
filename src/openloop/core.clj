(ns openloop.core
  (:gen-class)
  (use [overtone.core])
  (require [quil.core :as q])
  )

(defn -main
  "I don't do a whole lot ... yet."
  [& args]
  (println "Hello, World!"))

(defn boot
  "boot up the looper"
  []
  (connect-external-server)
  (use
   [openloop.constants]
   [openloop.fsm]
   [openloop.ui]
   [openloop.synths]
   [overtone.core]))


(use 'overtone.core)
(connect-external-server)
(server-info)
(pp-node-tree)


(println (status))
(defn groups
  "define groups"
  []
  (def in-group (group "in-group"))
  (def rec-group (group "rec-group" :after in-group))
  (def play-master-group (group "play-master-group" :after rec-group))
  (def play-slave-group (group "play-slave-group" :after play-master-group))
  (def out-group (group "out-group" :after play-slave-group)))

(defn setup
  "initialise"
  []
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

  (def in-synth (input [:head in-group]))
  (def out-synth (output [:head out-group]))
  (def m-rec-synth (master-rec [:head rec-group]))
  (def s-rec-synth1 (slave-rec [:tail rec-group] :which-buf 1 ))
  ;; can't do this unless you use
  ;; hack around index-in-between xruns:
  ;; see above
  ;; (def m-play-synth (master-play [:head play-master-group]))
  (pp-node-tree))

(defn start-master
  "start recording the master"
  []
  (ctl m-rec-synth :start 1)
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

(show-graphviz-synth master-play)


;; ******************************************************************************
;;              GUI
;; ******************************************************************************

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
      (reset! downbeatAtom 0))))

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

  (show-graphviz-synth slave-play3)
  (clear-all)
  (control-bus-monitor)
  (dotimes [i   nr-loops]
    (let [bufname (str "buffer" i)]
      ;; (buffer-fill! (symbol bufname) 0)
      (println (symbol bufname))))

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
