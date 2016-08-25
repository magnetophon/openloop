(ns openloop.core
  (:gen-class)
  (use [overtone.core]
       ;; [openloop.constants]
       ;; [openloop.boot]

       )
  (load-file "src/openloop/constants.clj")
  (load-file "src/openloop/boot.clj")
  (load-file "src/openloop/constants.clj")
  (load-file "src/openloop/states.clj")
  ;; (require
  ;;  ;; [quil.core :as q]
  ;;  [openloop.constants]
  ;;  [overtone.core]
  ;;  [openloop.boot]
  ;;  ;; [openloop.functions]
  )
)

;; (defonce __AUTO-CONNECT__
(def __AUTO-CONNECT__
  (when (server-disconnected?)
    (connect-external-server)
    ;; (boot-server-and-mixer)
    ))

(declare init)
(defn -main
  "I don't do a whole lot ... yet."
  [& args]
  (println "Hello, World!")
  (boot)
  (Thread/sleep 5000)
  (init)
  )

(-main)
;; (kill-server)

;; (server-connected?)
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
