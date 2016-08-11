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
  (println "start booting")
  (if (server-connected?)
    (do
      (println "we are connected:")
      (println (server-info))
      (wait-until-mixer-booted)
      (println (status))
      (load-file "src/openloop/constants.clj")
      (load-file "src/openloop/fsm.clj")
      ;; (load-file "src/openloop/ui.clj")
      (load-file "src/openloop/synths.clj")
      (load-file "src/openloop/init.clj")
      (println "all files loaded, setting up")
      )
    (do
      (println "connecting to server")
      (connect-external-server)
      (apply-at 1000 (boot)))))

(kill-server)
(boot)
( quil.applet/with-applet openloop.core/loop
 (q/background 000)
 )
(init)
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

(pp-node-tree)

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
