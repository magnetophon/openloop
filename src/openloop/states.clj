(ns openloop.core
  (require
   ;; [automat.viz :refer (view)]
   ;; [automat.core :as a]
   ;; [clojure.core.async :as as]
   [reduce-fsm :as fsm]
   [openloop.constants]))

(defn inc-val [val & _] (inc val))
(defn record-master
  "start recording the master loop"
  [loop-nr start-time]
  (println (str "recording master loop to" loop-nr)))


(fsm/defsm-inc loop-fsm
  [[:idle
    :loop-btn-down -> {:action record-master} :rec-master
    :tap -> {:action inc-val} :start-counting]
   [:rec-master
    :loop-btn-down ->  :play-master
    :timeout -> :idle]
   [:start-counting
    :tap -> {:action inc-val} :counting
    :loop-btn-down -> :rec-master
    :timeout -> :idle
    ]
   [:counting
    :tap -> {:action inc-val} :counting
    :loop-btn-down -> :rec-master
    :timeout -> :rec-master
    ]
   [:play-master
    :loop-btn-down -> :rec-master
    ]])

(fsm/show-fsm loop-fsm)

(def looper-state
  {:booted false,
   :connected false,
   :master-length 0,
   :master-offset 0,
   :all-loops
   [{:length 0,
     :sources
     {:audio
      {
       0 {:src-index 0, :x-fade-data {:length 0}}},
      :osc
      {:initial-values 0,
       :sources-vector [],
       :function :openloop.osc_functions/sum}},
     :FX
     {:internal 0, :external {:jack-name "", :command "", :port 0}}}]})

(pprint (s/unform ::looper-state-type looper-state))
(pprint looper-state)
(pprint (s/explain-data ::looper-state-type (s/unform ::looper-state-type looper-state)))
(s/conform ::looper-state-type '(true true 773533 -167259015 []))

(println "ooooooooooooooooooooooooooooooooooooo")
(pprint looper-state)

(def fsm-state (atom (loop-fsm  looper-state)))
(:FX (first (:all-loops (:value @fsm-state))))
(:value @fsm-state)
(:state @fsm-state)

(defn should-transition? [[state event]]
  (= (* state 2) event))

(defn event-is-even? [[state event]]
  (even? event))

(defn inc-count [count & _ ]
  (println (str "cnt: " count))
  (inc count)
  )


(defn reset-count [& _]
  0)

;; transition to the next state when we get a value thats twice the number of even events we've seen
(fsm/defsm-inc even-example
  [[:start
    [_ :guard should-transition?] -> {:action reset-count} :next-state
    [_ :guard event-is-even?] -> {:action inc-count} :start]
   [:next-state ,,,]]
  :default-acc  0
  :dispatch :event-acc-vec)

(def even-fsm-state (atom (even-example  0)))
(swap! even-fsm-state fsm/fsm-event 2)
(:value @even-fsm-state)
(:event @even-fsm-state)
(:state @even-fsm-state)
(even-example [1 1 2])   ;; => 1 (the number of even events)
(even-example [1 2 2 4]) ;; => 0 (we transitioned to next state)

(fsm/show-fsm even-example)
(defn fsm-step
  ""
  []
  )


(swap! fsm-state fsm/fsm-event :loop-btn-down)
(swap! fsm-state fsm/fsm-event :tap)
(swap! fsm-state fsm/fsm-event :timeout)
(swap! fsm-state fsm/fsm-event :other-loop-btn)
(loop-fsm 0)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; define input state
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(def inputs
  {:loop-btn-downs (boolean-array nr-loops)
   :tap false
   :timeout false
   })
(pprint inputs)
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; define loop data
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(def all-loops [ a-loop ]) ; a list of all loops contributing to the output.
;; a-loop has everything we need to:
;;    - play a loop
;;    - change a loop
;;    - reconstruct it from dry signal
;;    - undo / redo
(def a-loop {
             :loop-nr 0 ; which of the nr-loops buffers are we?
             :audio loop-audio ; the actual audio, dry and wet (effected)
             :FX loop-FX ; the effect chain that gets dry to wet
             :osc loop-osc ; the automation for the FX
             :prev-sources prev-sources-array}) ; for each point in the buffer, define where the data came from
(def loop-audio {:dry-audio
                 :wet-audio })
(def loop-FX nil)
(def loop-osc nil)
(def prev-sources-array {:audio source-audio-array :osc loop-osc-array}) ; a list of mutations to the audio and osc
(def source-audio-array [source-audio-block]) ; a chronological vector  of mutations to the loop audio buffer
(def source-audio-block ; a block of source audio mapped to a block of buffer audio
  {
   :start-loop 0 ; where does it go in the buffer
   :length 0 ; how long is the block of audio that we want to replace
   :start-source 0 ; where does it come from
   :xfade-data x-fade; how should we x-fade from the previous audio to the new and back
   })
(def x-fade
  {:xfade-length 10 ;how long should it take to x-fade
   :xfade-offset 0 ;where should the center of the x-fade be, relative to the start position
                                        ; possibly other data, like shape
   })
(def loop-osc-array [loop-osc-block] ) ; a chronological vector of mutations
(def loop-osc-block ; a block of source audio mapped to a block of buffer audio
  {:length 0 ; how long is the block of audio that we want to replace
   :start-loop 0 ; where does it go in the buffer
   :osc-sources [0] ; where does it come from, an array of input osc start indexes, each representing a stream of osc data
   :transform nil ; what function did we use to get from the osc sources to the output
   })

(def loop0 a-loop)
(:loop-nr loop0)
(:audio loop0)
(:audio
 (:prev-sources loop0))
(:osc
 (:prev-sources loop0))


(def PORT 4242)

                                        ; start a server and create a client to talk with it
(def server (osc-server PORT))
(def client (osc-client "localhost" PORT))

                                        ; Register a handler function for the /test OSC address
                                        ; The handler takes a message map with the following keys:
                                        ;   [:src-host, :src-port, :path, :type-tag, :args]
(osc-handle server "/test" (fn [msg] (println "MSG: " msg)))

                                        ; send it some messages
(doseq [val (range 10)]
  (osc-send client "/test" "i" val "some more"))

(Thread/sleep 1000)

                                        ;remove handler
(osc-rm-handler server "/test")

                                        ; stop listening and deallocate resources
(osc-close client)
(osc-close server)


;; maps

(def existingMap {:dog 1 :cat 2 :zebra 991})
(into (sorted-map) existingMap)
;; vectors

;; A vector is similar to an array, in that it’s a 0-indexed collection.
(get ["a" {:name "Pugsley Winterbottom"} "c"] 1)
;; You can create vectors with the vector function:
(vector "creepy" "full" "moon")
;; You can use the conj function to add additional elements to the vector.
;; Elements are added to the end of a vector:
(conj [1 2 3] 4)

;; lists

;; Lists are similar to vectors in that they’re linear collections of values. But there are some differences.
;; You can’t retrieve list elements with get.
;; To write a list literal, just insert the elements into parentheses and use a single quote at the beginning:
'(1 2 3 4)
(list 1 "two" {3 4})
;; Elements are added to the beginning of a list:
(conj '(1 2 3) 4)
;; A good rule of thumb is that if you need to easily add items to the beginning of a sequence or if you’re writing a macro, you should use a list. Otherwise, you should use a vector.

;; Sets

;; Sets are collections of unique values.
#{"kurt vonnegut" 20 :icicle}
(hash-set 1 1 2 2)
                                        ; => #{1 2}
