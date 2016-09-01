(ns openloop.core
  ;; (require
  ;; name
  ;;  [openloop.synths]
  ;;  )
  )
(defn groups
  "define groups"
  []
  (def in-group (group "in-group"))
  (def rec-group (group "rec-group" :after in-group))
  (def play-group (group "play-group" :after rec-group))
  ;; (def play-master-group (group "play-master-group" :after rec-group))
  ;; (def play-slave-group (group "play-slave-group" :after play-master-group))
  (def out-group (group "out-group" :after play-group)))

(defn defbuf
  "create a buffer"
  [i]
  (let [bufname (str "buffer" i)
        ]
    (eval `(defonce ~(symbol bufname)  (buffer max-loop-samples nr-chan ~(symbol bufname))))
    ))

(defn clearbuf
  "empty out a buffer"
  [i]
  (let [bufname (str "buffer" i)
        bufname (symbol bufname)]
    (buffer-fill! (eval bufname) 0)
    ))
;; gives a loud thump in the output:
;; Silent buffer used to fill in the gaps.
;; (defonce ^:private silent-buffer (buffer 0))
;; (buffer-fill! buffer0 (:id silent-buffer))

(defn def-loop-player
  "create a loop player"
  [i]
  (let
      [name (str "slave-play-synth" i)
       ]
    (eval `(def ~(symbol name) (loop-slave-play [:tail play-group])))
    ))



(defn init
  "initialise"
  []

  (when (disk-recording?)
    (println "stopping recording")
    (println "session saved in: " (get-in @fsm-state [:value :recorder :buf-stream :path] ))
    (disk-recording-stop)
    )
  (clear-all)
  ;; (def SR (:sample-rate (server-info)))
  ;; (def max-loop-samples (* max-loop-seconds SR))
  (groups)

  (dotimes [i nr-loops]
    (defbuf i)
    (Thread/sleep 1)
    (clearbuf i)
    (def-loop-player i)
    )

  ;; (defonce buffer0 (buffer max-loop-samples nr-chan buffer0))
  ;; (defonce buffer1 (buffer max-loop-samples nr-chan buffer1))
  ;; (defonce buffer2 (buffer max-loop-samples nr-chan buffer2))
  ;; (defonce buffer3 (buffer max-loop-samples nr-chan buffer3))
  ;; (defonce buffer4 (buffer max-loop-samples nr-chan buffer4))
  ;; (defonce buffer5 (buffer max-loop-samples nr-chan buffer5))
  ;; (defonce buffer6 (buffer max-loop-samples nr-chan buffer6))
  ;; (defonce buffer7 (buffer max-loop-samples nr-chan buffer7))

  ;; (buffer-fill! buffer0 0)
  ;; ;; hack around index-in-between xruns:
  ;; ;; (buffer-set! buffer0 0 0.003)
  ;; (buffer-fill! buffer1 0)
  ;; (buffer-fill! buffer2 0)
  ;; (buffer-fill! buffer3 0)
  ;; (buffer-fill! buffer4 0)
  ;; (buffer-fill! buffer5 0)
  ;; (buffer-fill! buffer6 0)
  ;; (buffer-fill! buffer7 0)

  ;;***********************************************************************************************
  ;; bus numbers
  ;;***********************************************************************************************

  (defonce dry-bus (audio-bus nr-chan))
  (defonce out-bus (audio-bus nr-chan))
  (defonce rec-clock-bus (audio-bus))
  (defonce short-clock-bus (audio-bus))
  (defonce long-clock-bus (audio-bus))
  ;; (dotimes)
  ;; (repeatedly nr-loops defbuf)
  ;; (doall)


  (def in-synth (input [:head in-group]))
  (disk-recording-start rec-group "/tmp/openloop.wav" :n-chans nr-chan :samples "float")
  (def master-clock-synth (master-clock [:tail rec-group]))
  (def ram-master-rec-synth (ram-master-rec [:tail rec-group]))
  (def loop-master-play-synth (loop-master-play [:tail play-group]))
  (def ram-slave-rec-synth (ram-slave-rec [:tail rec-group]))
  ;; (def loop-slave-play-synth (loop-slave-play [:tail play-group]))
  ;; (ctl loop-slave-play-synth  :which-buf 7)
  ;; (ctl loop-slave-play-synth  :now-bus 2000)
  (def out-synth (output [:head out-group]))
  ;; (def m-rec-synth (master-rec [:head rec-group]))
  ;; (def s-rec-synth1 (slave-rec [:tail rec-group] :which-buf 1 ))
  ;; can't do this unless you use
  ;; hack around index-in-between xruns:
  ;; see above
  ;; (def m-play-synth (master-play [:head play-master-group]))
  ;; (pp-node-tree)
  )

;;   (defonce ^:private silent-buffer (buffer 0))
;;   (buffer-fill! buffer0 (:id silent-buffer))


;; (clear-buf buffer0 )
;; (buffer-fill!)
;; (local-buf max-loop-samples nr-chan)
;; (event)
;; (block-node-until-ready?)


;; (defn start-master
;;   "start recording the master"
;;   []
;;   (ctl m-rec-synth :start 1)
;;   )

;; (defn stop-master
;;   "stop the master record and start playing it"
;;   []
;;   (ctl m-rec-synth :stop 1)
;;   (def m-play-synth (master-play [:head play-master-group]))
;;   )

;; (defn stop-slave
;;   "stop the slave record and start playing it"
;;   []
;;   (ctl s-rec-synth1 :stop 1)
;;   (def s-play-synth1 (slave-play [:head play-slave-group] :which-buf 1 ))
;;   )
