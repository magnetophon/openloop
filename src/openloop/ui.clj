(ns openloop.core
  (require [quil.core :as q]))


;; ******************************************************************************
;;              GUI
;; ******************************************************************************
(def downbeatAtom  (atom nil))

(defn setup []
  ;; (q/background 200)
  ;; (q/frame-rate 3)                 ;; Set the background colour to
  )

(println (str @downbeatAtom))
(defn draw []
  (q/background 100)
  (q/text "test" 10 20)
  (when (= @downbeatAtom 1)
    (do
      (q/text (str @downbeatAtom) 10 40)
      (q/background 255)
      (reset! downbeatAtom 0))))

(q/defsketch loop                  ;; Define a new sketch named example
  :title "Oh so much looping"    ;; Set the title of the sketch
  :settings #(q/smooth 2)             ;; Turn on anti-aliasing
  :setup setup                        ;; Specify the setup fn
  :draw draw                          ;; Specify the draw fn
  :size [300 300]                     ;; You struggle to beat the golden ratio
  :frame-rate 3
  :setup #(q/background 100)
  ;; :settings q/no-loop
  )

( quil.applet/with-applet openloop.ui/loop
 (q/background 200)
 )
