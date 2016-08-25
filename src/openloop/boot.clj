(ns openloop.core
  )

(import 'java.lang.Runtime)

;; (defonce __AUTO-BOOT__
(def __AUTO-BOOT__
  (when (server-disconnected?)
    (. (Runtime/getRuntime) exec "scsynth -u 57110")
    (Thread/sleep 2)
    (connect-external-server)
    ;; (boot-server-and-mixer)
    ))

(declare init)

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
      ;; the following files need these defs, but these defs need a booted synth
      (def SR (:sample-rate (server-info)))
      (def max-loop-samples (* max-loop-seconds SR))
      (load "synths")
      (load "init")
      (println "all openloop files loaded, setting up")
      )
    (do
      (println "not connected to server")
      )
    ))
