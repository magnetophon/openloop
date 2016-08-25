(ns openloop.core
  ;; (require
  ;;  [openloop.constants]
  ;;  [overtone.core]
  ;;  ;; [openloop.states]
  ;;  )
  )

(defn boot
  "boot up the looper"
  []
  (println "start booting")
  (if (server-connected?)
    (do
      (println "we are connected:")
      ;; (println (server-info))
      (wait-until-mixer-booted)
      ;; (println (status))
      (println "load openloop src files")
      ;; (load-file "src/openloop/constants.clj")
      ;; (load-file "src/openloop/states.clj")
      ;; (load-file "src/openloop/ui.clj")
      ;; these following files need these defs, but these defs need a booted synth
      (def SR (:sample-rate (server-info)))
      (def max-loop-samples (* max-loop-seconds SR))
      (load-file "src/openloop/synths.clj")
      (load-file "src/openloop/init.clj")
      (println "all openloop files loaded, setting up")
      )
    (do
      ;; (swap! fsm-state assoc-in [:value :booted] false)
      (println "not connected to server")
      ;; (connect-external-server)
      ;; (apply-at 5000 (boot)
      )
    ))
