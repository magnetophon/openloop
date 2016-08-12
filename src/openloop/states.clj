(ns openloop.states
  (require
   ;; [automat.viz :refer (view)]
   ;; [automat.core :as a]
   [reduce-fsm :as fsm]
   ))

(defn inc-val [val & _] (inc val))

(fsm/defsm-inc loop-fsm
  [[:idle
    :loop-btn -> :rec-master
    :tap -> {:action inc-val} :start-counting]
   [:rec-master
    :loop-btn ->  :play-master
    :timeout -> :idle]
   [:start-counting
    :tap -> {:action inc-val} :counting
    :loop-btn -> :rec-master
    :timeout -> :idle
    ]
   [:counting
    :tap -> {:action inc-val} :counting
    :loop-btn -> :rec-master
    :timeout -> :rec-master
    ]
   [:play-master
    :loop-btn -> :rec-master
    ]])

(fsm/show-fsm loop-fsm)

(def fsm-state (atom (loop-fsm  0)))
(:value @fsm-state)
(:state @fsm-state)

(swap! fsm-state fsm/fsm-event :loop-btn)
(swap! fsm-state fsm/fsm-event :tap)
(loop-fsm 0)


;; (view [1 2 3 4])
;; (view (a/or [1 2 3] [1 3]))
;; (partial)
;; (def f (a/compile
;;         [1 2 3 (a/$ :complete)]
;;         {:reducers {:complete (fn [state input] :completed)}})))
;; (partial a/advance f)
;; (def adv (partial a/advance f))
;; (a/advance f nil 42 :error)
;; (view f)
;; (def pages [:cart :checkout :cart])
;; (def page-pattern
;;   (vec
;;    (interpose (a/* a/any) pages)))
;; (interpose (a/* a/any) pages)
;; (page-pattern)
;; (view page-pattern)
;; (view (a/or [:sent :acknowledged :accepted] [:sent :rejected (a/$
;;                                                               :not-accepted)]))
;; (view (a/compile (a/or [:sent :acknowledged (a/or :accepted
;;                                                   :rejected)]
;;                        [:sent :malformed])))
;; (def )
;; (view (a/or [:loop-btn] [:]))o
