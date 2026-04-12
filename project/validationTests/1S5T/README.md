# 3S1T - Three Substations, One Train

Purpose:

- Test braking when the grid is not receptive at all

Input:

- grid.conf: network + tracks + return network
- run.csv: train motion + requested power (long format)

Expected observations (no exact numbers):

## Expected signals (qualitative + bounds)

Assumptions:

- During braking the grid is not receptive (no absorption possible).
- U_min_V and U_max_V are train voltage limits provided in the scenario (or agreed constants).

| Signal                         | Expected (accel / coast)                               | Expected (braking)                                          |
|--------------------------------|--------------------------------------------------------|-------------------------------------------------------------|
| Train.status                   | OK                                                     | NO_RECEPTIVITY                                              |
| Train.U_V                      | finite, positive, within [U_min_V, U_max_V]            | clamped at U_max_V (or within overvoltage handling), finite |
| Train.P_W (delivered/absorbed) | close to P_req_W (may be DERATED)                      | 0 (no absorption)                                           |
| Train.I_A                      | finite, sign consistent with traction consumption      | 0                                                           |
| Substation.status              | OK (or LIMIT if constraints hit)                       | OK (or LIMIT)                                               |
| Substation.U_V                 | finite, positive, within reasonable bounds             | finite, within reasonable bounds                            |
| Substation.P_W (net)           | supplies traction + losses (sum over substations > 0)  | approximately supplies only losses (sum > 0), not negative  |
| Total.P_losses_W (sum)         | > 0                                                    | > 0                                                         |
| Energy balance check           | sum(Substation.P_W) ≈ sum(Train.P_W) + sum(P_losses_W) | sum(Substation.P_W) ≈ sum(P_losses_W)                       |


