package commonResources;

public class UnsafeFieldUsage6 {

    private final Vehicle vehicle = new Vehicle();

    public UnsafeFieldUsage6() {

        Thread t1 = new Thread(new Runnable() {
            @Override
            public void run() {
                vehicle.setModel("Toyota");
            }
        });
        t1.start();

        Thread t2 = new Thread(
                () -> vehicle.setModel("Honda")
        );
        t2.start();

        Thread t3 = new Thread(this::createVehicle);
        t3.start();
    }

    private void createVehicle() {
        vehicle.setModel("Honda");
    }
}