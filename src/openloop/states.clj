(ns openloop.core
  (require
   ;; [automat.viz :refer (view)]
   ;; [automat.core :as a]
   ;; [clojure.core.async :as as]
   [reduce-fsm :as fsm]
   ))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; define input state
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(def inputs
  {:loop-btn-downs (boolean-array nr-loops)
   :tap false
   :timeout false
   })
;; (pprint inputs)
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; define loop data
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


;; a loop has everything we need to:
;;    - play a loop
;;    - change a loop
;;    - reconstruct it from dry signal
;;    - undo / redo
(def loop-init-data
  {:length 0,
   :sources
   {:audio
    {
     0 {:src-index 0,
        :x-fade-data {:length default-x-fade-length
                      :start (* -1 (/ default-x-fade-length 2))
                      }}}
    :osc
    {:initial-values 0,
     :sources-vector [],
     :function :openloop.osc_functions/sum}},
   :FX
   {:internal 0, :external {:jack-name "", :command "", :port 0}}}
  )

(def init-state
  {:master-length 0
   :master-offset 0
   :all-loops [loop-init-data]})



(def looper-state
  {:booted false,
   :connected false,
   :saved-state
   {:undo-index 0
    :undo-stack [init-state]}
   :inputs inputs
   })


;; (println "rrrrrrrrrrrrrrrr")
;; (pprint looper-state)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; define the state machine
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


;; (defn initialized? [[state event]]
;;   ;; remove boot while developing:
;;   ;; (swap! fsm-state assoc-in [:value :booted] true)
;;   ;; (boot)
;;   (init)
;;   (:booted state)
;;   )

(defn inc-val [val & _] (inc val))

(defn record-master
  "start recording the master loop"
  [state event fsm & action]
  (println (str "recording master loop to" fsm action))
  (vector state event)
  )

(fsm/defsm-inc loop-fsm
  [
   ;; [
   ;; :initializing
   ;; ;; [_ _ ] -> :idle  ]
   ;; [[_ _] :guard initialized?] -> :idle  ]
   [:idle
    [[_ :loop-btn-down]] -> {:action record-master} :rec-master
    [[_ :tap]] -> {:action inc-val} :start-counting]
   [:rec-master
    [[_ :loop-btn-down]] ->  :play-master
    [[_ :timeout]] -> :idle]
   [:start-counting
    [[_ :tap]] -> {:action inc-val} :counting
    [[_ :loop-btn-down]] -> :rec-master
    [[_ :timeout]] -> :idle
    ]
   [:counting
    [[_ :tap]] -> {:action inc-val} :counting
    [[_ :loop-btn-down]] -> :rec-master
    [[_ :timeout]] -> :rec-master
    ]
   [:play-master
    [[_ :loop-btn-down]] -> :rec-master
    ]]
  :dispatch :event-acc-vec
  ;; :dispatch :event-and-acc
  )

;; (fsm/show-fsm loop-fsm)

;; (println "ooooooooooooooooooooooooooooooooooooo")
;; (pprint looper-state)

(def fsm-state (atom (loop-fsm  looper-state)))

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
;; (swap! even-fsm-state fsm/fsm-event 2)
;; (:value @even-fsm-state)
;; (:event @even-fsm-state)
;; (:state @even-fsm-state)
;; (even-example [1 1 2])   ;; => 1 (the number of even events)
;; (even-example [1 2 2 4]) ;; => 0 (we transitioned to next state)

;; (loop-fsm 0)

;; (use 'overtone.osc)
;; (def PORT 4242)

;;                                         ; start a server and create a client to talk with it
;; (def server (osc-server PORT))
;; (def client (osc-client "localhost" PORT))

;;                                         ; Register a handler function for the /test OSC address
;;                                         ; The handler takes a message map with the following keys:
;;                                         ;   [:src-host, :src-port, :path, :type-tag, :args]
;; (osc-handle server "/test" (fn [msg] (println "MSG: " msg)))

;;                                         ; send it some messages
;; (println "ooooooooooooooooooooooooooooooooooooooooooo")
;; (doseq [val (range 10)]
;;   ;; (osc-send client "/test" (mod val 2) val))
;; ;; (osc-send client "/test" (even? val)))
;; (osc-send client "/test" "i" val "some more"))

;; (Thread/sleep 1000)

;;                                         ;remove handler
;; (osc-rm-handler server "/test")

;;                                         ; stop listening and deallocate resources
;; (osc-close client)
;; (osc-close server)
