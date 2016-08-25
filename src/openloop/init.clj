(ns openloop.core
  (require [overtone.core]
           [openloop.constants]
           [openloop.synths]))

(defn groups
  "define groups"
  []
  (def in-group (group "in-group"))
  (def rec-group (group "rec-group" :after in-group))
  (def play-master-group (group "play-master-group" :after rec-group))
  (def play-slave-group (group "play-slave-group" :after play-master-group))
  (def out-group (group "out-group" :after play-slave-group)))

(defn init
  "initialise"
  []
  (clear-all)
  ;; (def SR (:sample-rate (server-info)))
  ;; (def max-loop-samples (* max-loop-seconds SR))
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
  (pp-node-tree)
  (swap! fsm-state assoc-in [:value :booted] true)
  )

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
