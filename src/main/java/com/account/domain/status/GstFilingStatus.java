package com.account.domain.status;

    public enum GstFilingStatus {

        /**
         * Invoice GST return mein file nahi hua.
         */
        PENDING,

        /**
         * GST return mein file ho gaya,
         * lekin reconciliation pending hai.
         */
        FILED,

        /**
         * GST portal data aur invoice reconcile ho gaya.
         */
        RECONCILED

}
