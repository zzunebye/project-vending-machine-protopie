(ns vending-machine
  (:require [pretty.cli.prompt :as cli]
            [pretty.cli.figlet :as figlet]
            [pretty.cli.colors :as color-text]))

(def available-cash {:100 100, :500 100, :1000 100, :5000 100, :10000 10})

(def cash-type (set [100 500 1000 5000 10000]))

(def products {"Coke" {:price 1100, :quantity 5}, "Water" {:price 600, :quantity 5}, "Coffee" {:price 700, :quantity 0}})

(def initial-machine-state {:stage "idle"
                            :cash-in-machine available-cash
                            :products products})

(def initial-journey-state  {:selected-product nil
                             :inserted-cash 0
                             :change 0})


(defn is-balance-higher-than-price?
  [total price]
  (-> total
      (>= price)))

(defn refund-cash
  "Refund the cash inserted in the journey"
  [journey-state]
  (let [{:keys [inserted-cash]} @journey-state]
    (swap! journey-state assoc :inserted-cash 0)
    (println (color-text/cyan "Refunding cash:") inserted-cash)))

(defn calculate-change
  "Calculate change and update the journey state. The machine should decide which coin/cash to be used for returning change."
  [change available-cash]
  (let [sorted-cash (->> available-cash
                         (map (fn [[k v]] [(Integer/parseInt (name k)) v]))
                         (sort-by first >))
        result (atom {})]
    (loop [remaining change
           cash sorted-cash]
      (if (zero? remaining)
        (into {} (map (fn [[k v]] [(keyword (str k)) v]) @result))
        (if-let [[denom qty] (first cash)]
          (let [num-used (min (quot remaining denom) qty)]
            (if (pos? num-used)
              (do
                (swap! result assoc denom num-used)
                (recur (- remaining (* num-used denom))
                       (rest cash)))
              (recur remaining
                     (rest cash))))
          nil)))))

(defn select-product! 
  "Select a product from the vending machine"
  [machine-state journey-state]
  (let [{:keys [stage products]} @machine-state
        product-input (cli/list-select "Enter product" (keys products))]
    (try (cond (not (contains? products (str product-input)))
               (throw (ex-info "Invalid product" {}))
               (not (= stage "idle"))
               (throw (ex-info "Product already selected" {}))
               (< (get-in products [product-input :quantity]) 1)
               (throw (ex-info "Product out of stock" {}))
               :else
               (do (swap! machine-state assoc :stage "product-selected")
                   (swap! journey-state assoc :selected-product product-input)))
         (catch Exception e
           (println (color-text/red (str "Invalid product - " (ex-message e))))))))


(defn process-card-payment []
  (println "Processing card payment...")
  (Thread/sleep 1000)
  (println "Card payment processed")
  true)

(defn get-price-of-product [products product-name]
  (-> products
      (get product-name)
      :price))


(defn update-cash-in-machine
  ""
  [cash-in-machine-map cash-to-subtract-map]
  (println (color-text/cyan "Update internal state of cash in machine")))

(defn dispense-product! [machine-state journey-state]
  (swap! machine-state assoc :stage "dispensing")
  (println (color-text/cyan "Dispensing product..."))
  (Thread/sleep 1000)
  (println (color-text/cyan "Product dispensed"))
  (let [{:keys [selected-product]} @journey-state
        current-quantity (get-in @machine-state [:products selected-product :quantity])]
    (when (> current-quantity 0)
      (swap! machine-state update :products assoc-in [selected-product :quantity] (dec current-quantity)))))

(defn release-change! [change]
  (println (color-text/cyan "Releasing change: ") change)
  (Thread/sleep 1000)
  (println (color-text/cyan "Change released")))

(defn reset-journey! [machine-state journey-state]
  (swap! machine-state assoc :stage "idle")
  (reset! journey-state initial-journey-state))

(defn insert-cash! [machine-state journey-state]
  (let [{:keys [stage products]} @machine-state
        {:keys [selected-product]} @journey-state
        cash-input (cli/list-select "Enter cash" (map str (sort cash-type)))
        price (get-price-of-product products selected-product)]
    (cond (not (contains? cash-type (Integer/parseInt cash-input)))
          (println "Invalid cash type " (not (contains? cash-type (Integer/parseInt cash-input))))

          (= stage "idle")
          (println "You haven't chosen a product yet. Returning the cash immediately")

          :else
          (let [new-balance (+ (:inserted-cash @journey-state) (Integer/parseInt cash-input))]
            (swap! journey-state assoc :inserted-cash new-balance)
            (when (is-balance-higher-than-price? (:inserted-cash @journey-state) price)
              (println "Balance is higher than price. Proceeding to the dispense stage")
              (if-let [change (calculate-change (-> (:inserted-cash @journey-state)
                                                    (- price))
                                                (:cash-in-machine @machine-state))]
                (do
                  (dispense-product! machine-state journey-state)
                  (release-change! change)
                  (reset-journey! machine-state journey-state))
                (println "Change cannot be calculated")))))))

(defn insert-card! [machine-state journey-state]
  (println (color-text/cyan "Inserting card..."))
  (let [{:keys [stage]} @machine-state]
    (cond
      (= stage "idle")
      (println "You haven't chosen a product yet. Nothing is happening.")
      :else
      (if-let [result (process-card-payment)]
        (if (true? result)
          (do (println "Card payment successful")
              (dispense-product! machine-state journey-state)
              (refund-cash journey-state)
              (reset-journey! machine-state journey-state))
          (do
            (println "Card payment failed")
            (refund-cash journey-state)))
        (println "Card payment failed")))))

(defn check-balance 
  "Print out the current inserted balance"
  [journey-state]
  (let [{:keys [inserted-cash]} @journey-state]
    (println "Current balance: " inserted-cash)))

(defn cancel-transaction! [machine-state journey-state]
  (println (color-text/cyan "Cancelling transaction..."))
  (refund-cash journey-state)
  (reset-journey! machine-state journey-state))


(defn simulate-vending-machine []
  (println (figlet/figlet "Vending Machine"))
  (let [machine-state (atom initial-machine-state)
        journey-state (atom {:selected-product nil
                             :inserted-cash 0
                             :change 0})]
    (loop []
      (println (color-text/green "Current journey:") @journey-state)
      (println (color-text/green "Current stage:") (:stage @machine-state))
      (println (color-text/green "Current product stock:") (:products @machine-state))
      (let [input (cli/list-select "Choose action" ["Select Product" "Insert Cash" "Insert Card" "Check Balance" "Cancel"])]
        (case input
          "Insert Cash" (insert-cash! machine-state journey-state)
          "Insert Card" (insert-card! machine-state journey-state)
          "Select Product" (do (select-product! machine-state journey-state)
                               (future
                                 (Thread/sleep 10000)
                                 (when (= (:stage @machine-state) "product-selected")
                                   (reset-journey! machine-state journey-state)
                                   (println "Transaction timed out")
                                   (swap! machine-state assoc :stage "idle"))))
          "Check Balance" (check-balance journey-state)
          "Cancel" (cancel-transaction! machine-state journey-state)
          :else (println "Invalid input. Please enter 'exit' to leave the vending machine."))
        (recur)))))


(defn -main [& args]
  (simulate-vending-machine))