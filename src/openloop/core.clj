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
)

(defsynth index-synth [c-bus 0 rate 1]
  "the number of frames on disk"
  (let [trigger (impulse:ar (/ SR 2))
        slowtrigger (impulse:kr 1)

        ;; count (stepper:ar trigger :min 0 :max 3.3554432E7 :step 2)
        count (stepper:ar trigger :min 0 :max (* SR 60 60) :step 2)
        ]
    (send-trig:kr slowtrigger 0 (a2k count))
    ))

(show-graphviz-synth index-synth)
(on-event "/tr" #(println "samples: " (msg2int %) "   minutes: " (float (/ (msg2int %) (* SR 60)))) ::index-synth)
(remove-event-handler ::index-synth)

(defn msg2int
  "takes a message and returns the value in integer"
  [msg]
  (int (- (last (:args msg)) 3 )))


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
(disk-rec)
(disk-recording-start "/tmp/openloop.wav" :n-chans nr-chan :samples "float")

(-main)

(disk-recorder [:tail in-group] (apply buffer-stream "/tmp/openloop.wav"))

(swap! fsm-state assoc-in [:value :recorder] nil)

(pp-node-tree)
(clear-all)

(defn disk-rec
  "record a file and start counting samples"
  []
  (disk-recording-start "/tmp/openloop.wav" :n-chans nr-chan :samples "float"))

(defonce __DISKRECORDER__
  (defsynth disk-recorder
    [out-buf 0]
    (disk-out out-buf (in 0 2))))

(recording-start)
(defn disk-recording-start
  "Start recording a wav file to a new file at wav-path. Be careful -
  may generate very large files. See buffer-stream for a list of output
  options.
  Note, due to the size of the buffer used for transferring the audio
  from the audio server to the file, there will be 1.5s of silence at
  the start of the recording"
  [path & args]
  (if-let [info (:recorder (:value @fsm-state ))]
    ((throw (Exception. (str "Recording already taking place to: "
                             (get-in info [:buf-stream :path]))))))

  (let [
        ;; path (resolve-tilde-path path)
        bs   (apply buffer-stream path args)
        rec  (disk-recorder [:tail in-group] bs)]
    (swap! fsm-state assoc-in [:value :recorder] {:rec-id rec
                                    :buf-stream bs})
    :recording-started))


(defn disk-recording-stop
  "Stop system-wide recording. This frees the file and writes the wav headers.
  Returns the path of the file created."
  []
  (if-let [info (:recorder (:value @fsm-state))]
    (do
      (kill (:rec-id info))
      (buffer-stream-close (:buf-stream info))
      (swap! fsm-state assoc-in [:value :recorder] nil)
      (get-in info [:buf-stream :path]))
    (throw (Exception. (str " No recording already taking place."))))
  )

(kill pscope)
(disk-recording-stop)

(recording?)

(comment

  (key-state)
  (index-in-between)
  (lf-saw)
  recording?

  (tap)
  (sample-dur)
  (control-dur)

(def buffer-stream
  "Returns a buffer-stream which is similar to a regular buffer but may
  be used with the disk-out ugen to stream to a specific file on disk.
  Use #'buffer-stream-close to close the stream to finish recording to
  disk.
  Options:
  :n-chans     - Number of channels for the buffer
                 Default 2
  :size        - Buffer size
                 Default 65536
  :header      - Header format: \"aiff\", \"next\", \"wav\", \"ircam\", \"raw\"
                 Default \"wav\"
  :samples     - Sample format: \"int8\", \"int16\", \"int24\", \"int32\",
                                \"float\", \"double\", \"mulaw\", \"alaw\"
                 Default \"int16\"
  Example usage:
  (buffer-stream \"~/Desktop/foo.wav\" :n-chans 1 :header \"aiff\"
                                       :samples \"int32\")")

(defn recording?
  []
  (not (nil? (:recorder @studio*))))
)

(disk-out out-buf (in 0 2))))
(buffer-save buf \"~/Desktop/foo.wav\" :header \"aiff\" :samples \"int32\" :n-frames 10
             :start-frame 100)
(buffer-stream)

(show-graphviz-synth)

;; play:
(def foo1 (load-sample "~/gun1.wav" :start 9000 :size 4000))
(stereo-partial-player foo1 :loop? true)
;; (buffer-cue "~/gun2.wav" :start (* 3 44100) :size max-loop-length))
(buffer-cue)

(defn -main
  "I don't do a whole lot ... yet."
  [& args]
  (boot)
  (init)
  )

(-main)

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
