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
      [name (int-to-play-synth i)
       ]
                                        ;loop-; (eval `(def ~(symbol name) (loop-play  [:tail play-group ] :which-buf ~(int i ))))
    (eval `(def ~(symbol (str name)) (loop-play  [:tail play-group ] :which-buf ~(int i ))))
    ;; (eval `(def ~(symbol (str name)) (loop-play  [:tail play-group ] :which-buf ~(int (+ i 16) ))))
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

  (defonce lengths-buffer (buffer nr-loops 1 lengths-buffer))
  (buffer-fill! lengths-buffer 0)

  (def in-synth (input [:head in-group]))
  (disk-recording-start rec-group "/tmp/openloop.wav" :n-chans nr-chan :samples "float")
  (def command-handler-synth (command-handler [:tail rec-group]))
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
;;   (def s-play-synth1 (loop-play [:head play-slave-group] :which-buf 1 ))
;;   )
